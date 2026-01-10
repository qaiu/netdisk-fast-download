# Python解析器开发指南

## 概述

本指南介绍如何使用Python编写自定义网盘解析器。Python解析器基于GraalPy运行，提供与JavaScript解析器相同的功能，但使用Python语法。

## 目录

- [快速开始](#快速开始)
- [API参考](#api参考)
  - [ShareLinkInfo对象](#sharelinkinfo对象)
  - [PyHttpClient对象](#pyhttpclient对象)
  - [PyHttpResponse对象](#pyhttpresponse对象)
  - [PyLogger对象](#pylogger对象)
  - [PyCryptoUtils对象](#pycryptoutils对象)
- [实现方法](#实现方法)
  - [parse方法（必填）](#parse方法必填)
  - [parse_file_list方法（可选）](#parse_file_list方法可选)
  - [parse_by_id方法（可选）](#parse_by_id方法可选)
- [重定向处理](#重定向处理)
- [错误处理](#错误处理)
- [调试技巧](#调试技巧)
- [与JavaScript解析器的差异](#与javascript解析器的差异)
- [最佳实践](#最佳实践)
- [完整示例](#完整示例)

## 快速开始

### 1. 创建Python脚本

在 `./custom-parsers/py/` 目录下创建 `.py` 文件，使用以下模板：

```python
# ==UserScript==
# @name         我的解析器
# @type         my_parser
# @displayName  我的网盘
# @description  使用Python实现的网盘解析器
# @match        https?://example\.com/s/(?P<KEY>\w+)
# @author       yourname
# @version      1.0.0
# ==/UserScript==

def parse(share_link_info, http, logger):
    """
    解析单个文件下载链接
    
    Args:
        share_link_info: 分享链接信息对象
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        str: 直链下载地址
    """
    url = share_link_info.get_share_url()
    logger.info(f"开始解析: {url}")
    
    response = http.get(url)
    if not response.ok():
        raise Exception(f"请求失败: {response.status_code()}")
    
    # 解析响应获取下载链接
    return "https://example.com/download/file.zip"
```

### 2. 解析器元数据

Python解析器使用与JavaScript相同的元数据格式：

| 字段 | 必填 | 说明 |
|------|------|------|
| @name | 是 | 解析器名称 |
| @type | 是 | 解析器类型标识（小写字母和下划线） |
| @displayName | 是 | 显示名称 |
| @match | 是 | URL匹配正则表达式 |
| @description | 否 | 解析器描述 |
| @author | 否 | 作者名称 |
| @version | 否 | 版本号 |

### 3. 正则表达式命名分组

Python使用 `(?P<NAME>pattern)` 语法定义命名分组：

```python
# Python正则表达式命名分组
# @match https?://pan\.example\.com/s/(?P<KEY>\w+)(?:\?pwd=(?P<PWD>\w+))?
```

> **注意**：这与JavaScript的 `(?<NAME>pattern)` 语法不同。

## API参考

### ShareLinkInfo对象

提供分享链接信息的访问接口。

```python
# 获取分享URL
url = share_link_info.get_share_url()
# 或使用驼峰命名
url = share_link_info.getShareUrl()

# 获取分享Key
key = share_link_info.get_share_key()

# 获取分享密码
password = share_link_info.get_share_password()

# 获取网盘类型
pan_type = share_link_info.get_type()

# 获取网盘名称
pan_name = share_link_info.get_pan_name()

# 获取文件ID（用于parseById）
file_id = share_link_info.get_file_id()

# 获取其他参数
dir_id = share_link_info.get_other_param("dirId")
custom_value = share_link_info.get_other_param("customKey")
```

### PyHttpClient对象

提供HTTP请求功能，支持GET、POST、PUT、DELETE、PATCH等方法。

#### 基本请求

```python
# GET请求
response = http.get("https://api.example.com/data")

# POST请求（表单数据）
response = http.post("https://api.example.com/login", {
    "username": "user",
    "password": "pass"
})

# POST请求（JSON数据）
response = http.post_json("https://api.example.com/api", {
    "action": "get_file",
    "file_id": "12345"
})

# PUT请求
response = http.put("https://api.example.com/update", {"key": "value"})

# DELETE请求
response = http.delete("https://api.example.com/resource/123")

# PATCH请求
response = http.patch("https://api.example.com/resource/123", {"status": "active"})
```

#### 请求头设置

```python
# 设置单个请求头
http.put_header("Referer", "https://example.com")
http.put_header("Authorization", "Bearer token123")

# 链式设置多个请求头
http.put_header("Accept", "application/json").put_header("X-Custom", "value")

# 清除请求头
http.clear_headers()
```

#### 超时设置

```python
# 设置超时时间（秒）
http.set_timeout(60)  # 60秒超时
```

#### 重定向控制

```python
# 自动跟随重定向（默认行为）
response = http.get(url)

# 不跟随重定向，获取重定向URL
response = http.get_no_redirect(url)
if response.status_code() == 302:
    redirect_url = response.header("Location")
    logger.info(f"重定向到: {redirect_url}")
```

### PyHttpResponse对象

HTTP响应对象，提供响应数据访问。

```python
# 检查请求是否成功（2xx状态码）
if response.ok():
    # 处理成功响应
    pass

# 获取状态码
status = response.status_code()

# 获取响应文本
html = response.text()

# 获取响应JSON（自动解析）
data = response.json()
file_list = data.get("files", [])

# 获取响应头
content_type = response.header("Content-Type")
all_headers = response.headers()

# 获取响应字节
raw_bytes = response.body_bytes()

# 获取内容长度
length = response.content_length()

# 检查是否为重定向
if response.is_redirect():
    location = response.header("Location")
```

### PyLogger对象

提供日志记录功能。

```python
# 调试日志
logger.debug("变量值: " + str(value))
logger.debug(f"处理第 {index} 个文件")

# 信息日志
logger.info("开始解析文件列表")
logger.info(f"找到 {count} 个文件")

# 警告日志
logger.warn("密码为空，尝试无密码访问")
logger.warn(f"重试第 {retry} 次")

# 错误日志
logger.error("解析失败")
logger.error(f"HTTP错误: {status}")
```

### PyCryptoUtils对象

提供常用加密功能。

```python
# MD5加密
md5_hash = crypto.md5("hello")  # 32位小写
md5_16 = crypto.md5_16("hello")  # 16位小写

# SHA系列
sha1_hash = crypto.sha1("hello")
sha256_hash = crypto.sha256("hello")
sha512_hash = crypto.sha512("hello")

# Base64编码/解码
encoded = crypto.base64_encode("hello")
decoded = crypto.base64_decode(encoded)

# Base64 URL安全编码
url_safe = crypto.base64_url_encode("hello")
decoded = crypto.base64_url_decode(url_safe)

# AES加密/解密（ECB模式）
encrypted = crypto.aes_encrypt("plaintext", "1234567890123456")
decrypted = crypto.aes_decrypt(encrypted, "1234567890123456")

# AES加密/解密（CBC模式）
encrypted = crypto.aes_encrypt_cbc("plaintext", "1234567890123456", "1234567890123456")
decrypted = crypto.aes_decrypt_cbc(encrypted, "1234567890123456", "1234567890123456")

# 字节转十六进制
hex_str = crypto.bytes_to_hex(byte_array)
```

## 实现方法

### parse方法（必填）

解析单个文件的下载链接。

```python
def parse(share_link_info, http, logger):
    """
    解析分享链接，获取直链下载地址
    
    Args:
        share_link_info: 分享链接信息
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        str: 直链下载地址
    
    Raises:
        Exception: 解析失败时抛出异常
    """
    url = share_link_info.get_share_url()
    key = share_link_info.get_share_key()
    
    logger.info(f"解析分享: {url}")
    
    # 1. 获取页面或API数据
    response = http.get(url)
    if not response.ok():
        raise Exception(f"请求失败: {response.status_code()}")
    
    # 2. 解析响应
    data = response.json()
    if data.get("code") != 0:
        raise Exception(f"API错误: {data.get('message')}")
    
    # 3. 返回下载链接
    download_url = data.get("data", {}).get("download_url")
    if not download_url:
        raise Exception("未找到下载链接")
    
    return download_url
```

### parse_file_list方法（可选）

解析文件列表，用于文件夹分享。

```python
def parse_file_list(share_link_info, http, logger):
    """
    解析文件列表
    
    Args:
        share_link_info: 分享链接信息
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        list: 文件信息列表
    """
    key = share_link_info.get_share_key()
    dir_id = share_link_info.get_other_param("dirId") or "0"
    
    logger.info(f"获取文件列表，目录ID: {dir_id}")
    
    # 请求文件列表API
    response = http.post_json("https://api.example.com/list", {
        "share_key": key,
        "dir_id": dir_id
    })
    
    if not response.ok():
        raise Exception(f"请求失败: {response.status_code()}")
    
    data = response.json()
    files = data.get("files", [])
    
    # 转换为标准格式
    result = []
    for file in files:
        result.append({
            "file_name": file.get("name"),
            "file_id": file.get("id"),
            "file_type": "dir" if file.get("is_dir") else "file",
            "size": file.get("size", 0),
            "pan_type": share_link_info.get_type(),
            "parser_url": f"https://example.com/s/{key}?fid={file.get('id')}"
        })
    
    return result
```

### parse_by_id方法（可选）

根据文件ID获取下载链接。

```python
def parse_by_id(share_link_info, http, logger):
    """
    根据文件ID解析下载链接
    
    Args:
        share_link_info: 分享链接信息（包含file_id）
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        str: 直链下载地址
    """
    key = share_link_info.get_share_key()
    file_id = share_link_info.get_file_id()
    
    logger.info(f"解析文件ID: {file_id}")
    
    # 请求下载链接API
    response = http.post_json("https://api.example.com/download", {
        "share_key": key,
        "file_id": file_id
    })
    
    if not response.ok():
        raise Exception(f"请求失败: {response.status_code()}")
    
    data = response.json()
    return data.get("download_url")
```

## 重定向处理

很多网盘的下载链接需要通过重定向获取最终URL：

```python
def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    
    # 方法1：使用不跟随重定向的请求
    response = http.get_no_redirect(url)
    if response.is_redirect():
        download_url = response.header("Location")
        logger.info(f"重定向URL: {download_url}")
        return download_url
    
    # 方法2：跟随重定向获取最终URL
    response = http.get(url)
    final_url = response.header("X-Final-URL")  # 如果服务器提供
    
    return final_url
```

## 错误处理

```python
def parse(share_link_info, http, logger):
    try:
        url = share_link_info.get_share_url()
        response = http.get(url)
        
        # 检查HTTP状态
        if not response.ok():
            if response.status_code() == 404:
                raise Exception("分享链接不存在或已失效")
            elif response.status_code() == 403:
                raise Exception("需要密码访问")
            else:
                raise Exception(f"HTTP错误: {response.status_code()}")
        
        # 检查业务状态
        data = response.json()
        if data.get("code") != 0:
            error_msg = data.get("message", "未知错误")
            raise Exception(f"业务错误: {error_msg}")
        
        return data.get("download_url")
        
    except Exception as e:
        logger.error(f"解析失败: {str(e)}")
        raise
```

## 调试技巧

### 1. 使用日志输出

```python
def parse(share_link_info, http, logger):
    logger.info("===== 开始调试 =====")
    
    url = share_link_info.get_share_url()
    logger.debug(f"分享URL: {url}")
    
    response = http.get(url)
    logger.debug(f"状态码: {response.status_code()}")
    logger.debug(f"响应头: {response.headers()}")
    logger.debug(f"响应体前500字符: {response.text()[:500]}")
    
    # 解析逻辑...
```

### 2. 分步调试

```python
def parse(share_link_info, http, logger):
    # 步骤1：获取页面
    logger.info("步骤1：获取页面")
    response = http.get(share_link_info.get_share_url())
    logger.info(f"步骤1完成，状态: {response.status_code()}")
    
    # 步骤2：提取token
    logger.info("步骤2：提取token")
    html = response.text()
    # token = extract_token(html)
    # logger.info(f"Token: {token}")
    
    # 步骤3：请求下载API
    logger.info("步骤3：请求下载API")
    # ...
```

### 3. 使用演练场

在Web演练场中可以实时测试Python代码：
1. 打开演练场页面
2. 选择Python语言
3. 输入测试URL和代码
4. 点击运行查看日志输出

## 与JavaScript解析器的差异

| 特性 | JavaScript | Python |
|------|------------|--------|
| 正则命名分组 | `(?<NAME>pattern)` | `(?P<NAME>pattern)` |
| 字符串格式化 | `模板字符串` | `f"字符串"` 或 `.format()` |
| 异常处理 | `try/catch` | `try/except` |
| 空值判断 | `null/undefined` | `None` |
| 布尔值 | `true/false` | `True/False` |
| 字典访问 | `obj.key` 或 `obj["key"]` | `dict.get("key")` 或 `dict["key"]` |
| 列表操作 | `array.push()` | `list.append()` |

### 示例对比

**JavaScript:**
```javascript
function parse(shareLinkInfo, http, logger) {
    var url = shareLinkInfo.getShareUrl();
    logger.info("解析: " + url);
    
    var response = http.get(url);
    if (!response.ok()) {
        throw new Error("请求失败");
    }
    
    var data = response.json();
    return data.download_url || "";
}
```

**Python:**
```python
def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    logger.info(f"解析: {url}")
    
    response = http.get(url)
    if not response.ok():
        raise Exception("请求失败")
    
    data = response.json()
    return data.get("download_url", "")
```

## 最佳实践

### 1. 使用Python风格的方法名

```python
# 推荐：Python下划线风格
url = share_link_info.get_share_url()
key = share_link_info.get_share_key()

# 也支持：Java驼峰风格
url = share_link_info.getShareUrl()
key = share_link_info.getShareKey()
```

### 2. 合理设置超时

```python
def parse(share_link_info, http, logger):
    # 对于慢速API，增加超时时间
    http.set_timeout(60)
    
    # 对于需要多次请求的场景
    http.set_timeout(30)  # 每次请求30秒
```

### 3. 处理编码问题

```python
# 响应自动处理UTF-8编码
text = response.text()

# 如果需要处理其他编码，可以获取原始字节
raw = response.body_bytes()
```

### 4. 请求头管理

```python
def parse(share_link_info, http, logger):
    # 设置必要的请求头
    http.put_header("Referer", share_link_info.get_share_url())
    http.put_header("User-Agent", "Mozilla/5.0 ...")
    
    response = http.get(url)
    
    # 如果需要清除请求头
    http.clear_headers()
```

## 完整示例

```python
# ==UserScript==
# @name         示例网盘解析器
# @type         example_pan
# @displayName  示例网盘
# @description  完整的Python解析器示例
# @match        https?://pan\.example\.com/s/(?P<KEY>\w+)(?:\?pwd=(?P<PWD>\w+))?
# @author       QAIU
# @version      1.0.0
# ==/UserScript==

"""
示例网盘Python解析器
展示完整的解析流程
"""

import re  # GraalPy支持标准库


def parse(share_link_info, http, logger):
    """解析单个文件下载链接"""
    url = share_link_info.get_share_url()
    key = share_link_info.get_share_key()
    password = share_link_info.get_share_password()
    
    logger.info(f"开始解析: {url}")
    logger.info(f"分享Key: {key}")
    
    # 设置请求头
    http.put_header("Referer", url)
    http.put_header("Accept", "application/json")
    
    # 步骤1：获取分享信息
    info_url = f"https://api.example.com/share/info?key={key}"
    response = http.get(info_url)
    
    if not response.ok():
        raise Exception(f"获取分享信息失败: {response.status_code()}")
    
    data = response.json()
    if data.get("code") != 0:
        raise Exception(f"分享不存在或已失效: {data.get('message')}")
    
    # 步骤2：验证密码（如果需要）
    if data.get("need_password") and password:
        verify_url = "https://api.example.com/share/verify"
        verify_response = http.post_json(verify_url, {
            "key": key,
            "password": password
        })
        
        if not verify_response.json().get("success"):
            raise Exception("密码错误")
    
    # 步骤3：获取下载链接
    download_url = f"https://api.example.com/share/download?key={key}"
    download_response = http.get_no_redirect(download_url)
    
    if download_response.is_redirect():
        final_url = download_response.header("Location")
        logger.info(f"获取到下载链接: {final_url}")
        return final_url
    
    # 备选方案：从响应中解析
    download_data = download_response.json()
    return download_data.get("url")


def parse_file_list(share_link_info, http, logger):
    """解析文件列表"""
    key = share_link_info.get_share_key()
    dir_id = share_link_info.get_other_param("dirId") or "0"
    
    logger.info(f"获取文件列表，目录: {dir_id}")
    
    list_url = f"https://api.example.com/share/list?key={key}&dir={dir_id}"
    response = http.get(list_url)
    
    if not response.ok():
        raise Exception(f"获取文件列表失败: {response.status_code()}")
    
    data = response.json()
    files = data.get("files", [])
    
    result = []
    for file in files:
        result.append({
            "file_name": file.get("name"),
            "file_id": str(file.get("id")),
            "file_type": "dir" if file.get("is_dir") else "file",
            "size": file.get("size", 0),
            "pan_type": share_link_info.get_type(),
            "parser_url": f"https://pan.example.com/s/{key}?fid={file.get('id')}"
        })
    
    logger.info(f"找到 {len(result)} 个文件/文件夹")
    return result


def parse_by_id(share_link_info, http, logger):
    """根据文件ID获取下载链接"""
    key = share_link_info.get_share_key()
    file_id = share_link_info.get_file_id()
    
    logger.info(f"解析文件: {file_id}")
    
    download_url = f"https://api.example.com/share/download?key={key}&fid={file_id}"
    response = http.get_no_redirect(download_url)
    
    if response.is_redirect():
        return response.header("Location")
    
    return response.json().get("url")
```

## 相关文档

- [JavaScript解析器开发指南](JAVASCRIPT_PARSER_GUIDE.md)
- [自定义解析器扩展指南](CUSTOM_PARSER_GUIDE.md)
- [API使用文档](API_USAGE.md)
