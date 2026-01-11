# Python解析器开发指南

## 概述

本指南介绍如何使用Python编写自定义网盘解析器。Python解析器基于GraalPy运行，提供与JavaScript解析器相同的功能，但使用Python语法。

### 技术规格

- **Python 运行时**: GraalPy (GraalVM Python)
- **Python 版本**: Python 3.10+ 兼容
- **标准库支持**: 支持大部分 Python 标准库
- **第三方库支持**: 内置 requests 库（需在顶层导入）
- **运行模式**: 同步执行，所有操作都是阻塞式的

### 参考文档

- **Python 官方文档**: https://docs.python.org/zh-cn/3/
- **Python 标准库**: https://docs.python.org/zh-cn/3/library/
- **GraalPy 文档**: https://www.graalvm.org/python/
- **Requests 库文档**: https://requests.readthedocs.io/

## 目录

- [快速开始](#快速开始)
- [API参考](#api参考)
  - [ShareLinkInfo对象](#sharelinkinfo对象)
  - [PyHttpClient对象](#pyhttpclient对象)
  - [PyHttpResponse对象](#pyhttpresponse对象)
  - [PyLogger对象](#pylogger对象)
  - [PyCryptoUtils对象](#pycryptoutils对象)
- [使用 requests 库](#使用-requests-库)
  - [基本使用](#基本使用)
  - [Session 会话](#session-会话)
  - [高级功能](#高级功能)
  - [注意事项](#注意事项)
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

## 使用 requests 库

GraalPy 环境支持使用流行的 Python requests 库来处理 HTTP 请求。requests 提供了更加 Pythonic 的 API，适合熟悉 Python 生态的开发者。

> **官方文档**: [Requests: HTTP for Humans™](https://requests.readthedocs.io/)

### 重要提示

**requests 必须在脚本顶层导入，不能在函数内部导入：**

```python
# ✅ 正确：在顶层导入
import requests

def parse(share_link_info, http, logger):
    response = requests.get(url)
    # ...

# ❌ 错误：在函数内导入
def parse(share_link_info, http, logger):
    import requests  # 这会失败！
```

### 基本使用

#### GET 请求

```python
import requests

def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    
    # 基本 GET 请求
    response = requests.get(url)
    
    # 检查状态码
    if response.status_code == 200:
        html = response.text
        logger.info(f"页面长度: {len(html)}")
    
    # 带参数的 GET 请求
    response = requests.get('https://api.example.com/search', params={
        'key': share_link_info.get_share_key(),
        'format': 'json'
    })
    
    # 自动解析 JSON
    data = response.json()
    return data['download_url']
```

#### POST 请求

```python
import requests

def parse(share_link_info, http, logger):
    # POST 表单数据
    response = requests.post('https://api.example.com/login', data={
        'username': 'user',
        'password': 'pass'
    })
    
    # POST JSON 数据
    response = requests.post('https://api.example.com/api', json={
        'action': 'get_download',
        'file_id': '12345'
    })
    
    # 自定义请求头
    response = requests.post(
        'https://api.example.com/upload',
        json={'file': 'data'},
        headers={
            'Authorization': 'Bearer token123',
            'Content-Type': 'application/json',
            'User-Agent': 'Mozilla/5.0 ...'
        }
    )
    
    return response.json()['url']
```

#### 设置请求头

```python
import requests

def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    
    # 自定义请求头
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': url,
        'Accept': 'application/json',
        'Accept-Language': 'zh-CN,zh;q=0.9',
        'X-Requested-With': 'XMLHttpRequest'
    }
    
    response = requests.get(url, headers=headers)
    return response.text
```

#### 处理 Cookie

```python
import requests

def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    
    # 方法1：使用 cookies 参数
    cookies = {
        'session_id': 'abc123',
        'user_token': 'xyz789'
    }
    response = requests.get(url, cookies=cookies)
    
    # 方法2：从响应中获取 Cookie
    response = requests.get(url)
    logger.info(f"返回的 Cookies: {response.cookies}")
    
    # 在后续请求中使用
    next_response = requests.get('https://api.example.com/data', 
                                  cookies=response.cookies)
    
    return next_response.json()['download_url']
```

### Session 会话

使用 Session 可以自动管理 Cookie，适合需要多次请求的场景：

```python
import requests

def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    key = share_link_info.get_share_key()
    
    # 创建 Session
    session = requests.Session()
    
    # 设置全局请求头
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 ...',
        'Referer': url
    })
    
    # 步骤1：访问页面，获取 Cookie
    logger.info("步骤1: 访问页面")
    response1 = session.get(url)
    
    # 步骤2：提交验证
    logger.info("步骤2: 验证密码")
    password = share_link_info.get_share_password()
    response2 = session.post('https://api.example.com/verify', data={
        'key': key,
        'pwd': password
    })
    
    # 步骤3：获取下载链接（Session 自动携带 Cookie）
    logger.info("步骤3: 获取下载链接")
    response3 = session.get(f'https://api.example.com/download?key={key}')
    
    data = response3.json()
    return data['url']
```

### 高级功能

#### 超时设置

```python
import requests

def parse(share_link_info, http, logger):
    try:
        # 设置 5 秒超时
        response = requests.get(url, timeout=5)
        
        # 分别设置连接超时和读取超时
        response = requests.get(url, timeout=(3, 10))  # 连接3秒，读取10秒
        
        return response.text
    except requests.Timeout:
        logger.error("请求超时")
        raise Exception("请求超时")
```

#### 重定向控制

```python
import requests

def parse(share_link_info, http, logger):
    url = share_link_info.get_share_url()
    
    # 不跟随重定向
    response = requests.get(url, allow_redirects=False)
    
    if response.status_code in [301, 302, 303, 307, 308]:
        download_url = response.headers['Location']
        logger.info(f"重定向到: {download_url}")
        return download_url
    
    # 限制重定向次数
    response = requests.get(url, allow_redirects=True, max_redirects=5)
    return response.text
```

#### 代理设置

```python
import requests

def parse(share_link_info, http, logger):
    # 使用代理
    proxies = {
        'http': 'http://proxy.example.com:8080',
        'https': 'https://proxy.example.com:8080'
    }
    
    response = requests.get(url, proxies=proxies)
    return response.text
```

#### 文件上传

```python
import requests

def parse(share_link_info, http, logger):
    # 上传文件
    files = {
        'file': ('filename.txt', 'file content', 'text/plain')
    }
    
    response = requests.post('https://api.example.com/upload', files=files)
    return response.json()['file_url']
```

#### 异常处理

```python
import requests
from requests.exceptions import RequestException, HTTPError, Timeout, ConnectionError

def parse(share_link_info, http, logger):
    try:
        response = requests.get(url, timeout=10)
        
        # 检查 HTTP 错误（4xx, 5xx）
        response.raise_for_status()
        
        return response.json()['download_url']
        
    except HTTPError as e:
        logger.error(f"HTTP 错误: {e.response.status_code}")
        raise
    except Timeout:
        logger.error("请求超时")
        raise
    except ConnectionError:
        logger.error("连接失败")
        raise
    except RequestException as e:
        logger.error(f"请求异常: {str(e)}")
        raise
```

### 注意事项

#### 1. 顶层导入限制

**requests 必须在脚本最顶部导入，不能在函数内部导入：**

```python
# ✅ 正确示例
import requests
import json
import re

def parse(share_link_info, http, logger):
    response = requests.get(url)
    # ...

# ❌ 错误示例
def parse(share_link_info, http, logger):
    import requests  # 运行时会报错！
    response = requests.get(url)
```

#### 2. 与内置 http 对象的选择

- **使用 requests**：适合熟悉 Python 生态、需要复杂功能（Session、高级参数）
- **使用内置 http**：更轻量、性能更好、适合简单场景

```python
import requests

def parse(share_link_info, http, logger):
    # 方式1：使用 requests（更 Pythonic）
    response = requests.get(url, headers={'User-Agent': 'Mozilla/5.0'})
    data = response.json()
    
    # 方式2：使用内置 http（更轻量）
    http.put_header('User-Agent', 'Mozilla/5.0')
    response = http.get(url)
    data = response.json()
    
    # 两种方式可以混用
    return data['url']
```

#### 3. 编码处理

```python
import requests

def parse(share_link_info, http, logger):
    response = requests.get(url)
    
    # requests 自动检测编码
    text = response.text
    logger.info(f"检测到编码: {response.encoding}")
    
    # 手动设置编码
    response.encoding = 'utf-8'
    text = response.text
    
    # 获取原始字节
    raw_bytes = response.content
    
    return text
```

#### 4. 性能考虑

```python
import requests

def parse(share_link_info, http, logger):
    # 使用 Session 复用连接（提升性能）
    session = requests.Session()
    
    # 多次请求时，Session 会复用 TCP 连接
    response1 = session.get('https://api.example.com/step1')
    response2 = session.get('https://api.example.com/step2')
    response3 = session.get('https://api.example.com/step3')
    
    return response3.json()['url']
```

### 完整示例：使用 requests

```python
# ==UserScript==
# @name         示例-使用requests
# @type         example_requests
# @displayName  requests示例
# @match        https?://pan\.example\.com/s/(?P<KEY>\w+)
# @version      1.0.0
# ==/UserScript==

import requests
import json

def parse(share_link_info, http, logger):
    """
    使用 requests 库的完整示例
    """
    url = share_link_info.get_share_url()
    key = share_link_info.get_share_key()
    password = share_link_info.get_share_password()
    
    logger.info(f"开始解析: {url}")
    
    # 创建 Session
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Referer': url,
        'Accept': 'application/json'
    })
    
    try:
        # 步骤1：获取分享信息
        logger.info("获取分享信息")
        response = session.get(
            f'https://api.example.com/share/info',
            params={'key': key},
            timeout=10
        )
        response.raise_for_status()
        
        info = response.json()
        if info['code'] != 0:
            raise Exception(f"分享不存在: {info['message']}")
        
        # 步骤2：验证密码
        if info.get('need_password') and password:
            logger.info("验证密码")
            verify_response = session.post(
                'https://api.example.com/share/verify',
                json={
                    'key': key,
                    'password': password
                },
                timeout=10
            )
            verify_response.raise_for_status()
            
            if not verify_response.json().get('success'):
                raise Exception("密码错误")
        
        # 步骤3：获取下载链接
        logger.info("获取下载链接")
        download_response = session.get(
            f'https://api.example.com/share/download',
            params={'key': key},
            allow_redirects=False,
            timeout=10
        )
        
        # 处理重定向
        if download_response.status_code in [301, 302]:
            download_url = download_response.headers['Location']
            logger.info(f"获取到下载链接: {download_url}")
            return download_url
        
        # 或从 JSON 中提取
        download_response.raise_for_status()
        data = download_response.json()
        return data['url']
        
    except requests.Timeout:
        logger.error("请求超时")
        raise Exception("请求超时，请稍后重试")
    except requests.HTTPError as e:
        logger.error(f"HTTP 错误: {e.response.status_code}")
        raise Exception(f"HTTP 错误: {e.response.status_code}")
    except requests.RequestException as e:
        logger.error(f"请求失败: {str(e)}")
        raise Exception(f"请求失败: {str(e)}")
    except Exception as e:
        logger.error(f"解析失败: {str(e)}")
        raise


def parse_file_list(share_link_info, http, logger):
    """
    使用 requests 解析文件列表
    """
    key = share_link_info.get_share_key()
    dir_id = share_link_info.get_other_param("dirId") or "0"
    
    logger.info(f"获取文件列表: {dir_id}")
    
    try:
        response = requests.get(
            'https://api.example.com/share/list',
            params={'key': key, 'dir': dir_id},
            headers={'User-Agent': 'Mozilla/5.0 ...'},
            timeout=10
        )
        response.raise_for_status()
        
        data = response.json()
        files = data.get('files', [])
        
        result = []
        for file in files:
            result.append({
                'file_name': file['name'],
                'file_id': str(file['id']),
                'file_type': 'dir' if file.get('is_dir') else 'file',
                'size': file.get('size', 0),
                'pan_type': share_link_info.get_type(),
                'parser_url': f'https://pan.example.com/s/{key}?fid={file["id"]}'
            })
        
        logger.info(f"找到 {len(result)} 个文件")
        return result
        
    except requests.RequestException as e:
        logger.error(f"获取文件列表失败: {str(e)}")
        raise
```

### requests 官方资源

- **官方文档**: https://requests.readthedocs.io/
- **快速入门**: https://requests.readthedocs.io/en/latest/user/quickstart/
- **高级用法**: https://requests.readthedocs.io/en/latest/user/advanced/
- **API 参考**: https://requests.readthedocs.io/en/latest/api/

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

### 项目文档
- [JavaScript解析器开发指南](JAVASCRIPT_PARSER_GUIDE.md)
- [自定义解析器扩展指南](CUSTOM_PARSER_GUIDE.md)
- [API使用文档](API_USAGE.md)
- [Python LSP WebSocket集成指南](PYLSP_WEBSOCKET_GUIDE.md)
- [Python演练场测试报告](PYTHON_PLAYGROUND_TEST_REPORT.md)

### 外部资源
- [Requests 官方文档](https://requests.readthedocs.io/) - HTTP for Humans™
- [Requests 快速入门](https://requests.readthedocs.io/en/latest/user/quickstart/)
- [Requests 高级用法](https://requests.readthedocs.io/en/latest/user/advanced/)
- [GraalPy 官方文档](https://www.graalvm.org/python/)
