# 自定义解析器API使用指南

## 📡 API端点

当你在演练场发布自定义解析器后，可以通过以下API端点使用：

---

## 1️⃣ 302重定向（直接下载）

**端点**: `/parser`

**方法**: `GET`

**描述**: 返回302重定向到实际下载地址，适合浏览器直接访问下载

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | string | ✅ 是 | 分享链接（需URL编码） |
| pwd | string | ❌ 否 | 分享密码 |
| auth | string | ❌ 否 | 认证参数（AES加密后的JSON，用于需要登录的网盘） |

### 认证参数说明（v0.2.1+）

部分网盘（如夸克QK、UC网盘）需要登录后的 Cookie 才能解析。`auth` 参数用于传递认证信息：

**加密方式**：
- 算法：AES/ECB/PKCS5Padding
- 密钥：`nfd_auth_key2026`（16字节）
- 流程：JSON → AES加密 → Base64 → URL编码

**JSON 结构**：
```json
{
  "authType": "cookie",        // 认证类型: cookie/accesstoken/authorization
  "token": "your_cookie_here"  // Cookie 或 Token 内容
}
```

**网盘认证要求**：
| 网盘 | 认证要求 |
|------|---------|
| 夸克网盘(QK) | **必须** |
| UC网盘(UC) | **必须** |
| 小飞机网盘(FJ) | 大文件需要 |
| 蓝奏优享(IZ) | 大文件需要 |

### 请求示例

```bash
# 基本请求
GET http://localhost:6400/parser?url=https://lanzoui.com/i7Aq12ab3cd

# 带密码
GET http://localhost:6400/parser?url=https://lanzoui.com/i7Aq12ab3cd&pwd=1234

# curl命令
curl -L "http://localhost:6400/parser?url=https://lanzoui.com/i7Aq12ab3cd"
```

### 响应

```http
HTTP/1.1 302 Found
Location: https://download-server.com/file/xxx
```

浏览器会自动跳转到下载地址。

---

## 2️⃣ JSON响应（获取解析结果）

**端点**: `/json/parser`

**方法**: `GET`

**描述**: 返回JSON格式的解析结果，包含下载链接等详细信息

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | string | ✅ 是 | 分享链接（需URL编码） |
| pwd | string | ❌ 否 | 分享密码 |

### 请求示例

```bash
# 基本请求
GET http://localhost:6400/json/parser?url=https://lanzoui.com/i7Aq12ab3cd

# 带密码
GET http://localhost:6400/json/parser?url=https://lanzoui.com/i7Aq12ab3cd&pwd=1234

# curl命令
curl "http://localhost:6400/json/parser?url=https://lanzoui.com/i7Aq12ab3cd"
```

### 响应格式

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "url": "https://download-server.com/file/xxx",
    "fileName": "example.zip",
    "fileSize": "10MB",
    "parseTime": 1234
  }
}
```

---

## 🔧 使用场景

### 场景1: 浏览器直接下载

用户点击链接直接下载：

```html
<a href="http://localhost:6400/parser?url=https://lanzoui.com/i7Aq12ab3cd">
  点击下载
</a>
```

### 场景2: 获取下载信息

JavaScript获取下载链接：

```javascript
fetch('http://localhost:6400/json/parser?url=https://lanzoui.com/i7Aq12ab3cd')
  .then(res => res.json())
  .then(data => {
    console.log('下载链接:', data.data.url);
    console.log('文件名:', data.data.fileName);
  });
```

### 场景3: 命令行下载

```bash
# 方式1: 直接下载
curl -L -O "http://localhost:6400/parser?url=https://lanzoui.com/i7Aq12ab3cd"

# 方式2: 先获取链接再下载
DOWNLOAD_URL=$(curl -s "http://localhost:6400/json/parser?url=https://lanzoui.com/i7Aq12ab3cd" | jq -r '.data.url')
curl -L -O "$DOWNLOAD_URL"
```

### 场景4: Python脚本

```python
import requests

# 获取解析结果
response = requests.get(
    'http://localhost:6400/json/parser',
    params={
        'url': 'https://lanzoui.com/i7Aq12ab3cd',
        'pwd': '1234'
    }
)

result = response.json()
if result['code'] == 200:
    download_url = result['data']['url']
    print(f'下载链接: {download_url}')
    
    # 下载文件
    file_response = requests.get(download_url)
    with open('download.file', 'wb') as f:
        f.write(file_response.content)
```

---

## 🎯 解析器匹配规则

系统会根据分享链接的URL自动选择合适的解析器：

1. **优先匹配自定义解析器**
   - 检查演练场发布的解析器
   - 使用 `@match` 正则表达式匹配

2. **内置解析器**
   - 如果没有匹配的自定义解析器
   - 使用系统内置的解析器

### 示例

假设你发布了蓝奏云解析器：

```javascript
// @match https?://lanzou[a-z]{1,2}\.com/(?<KEY>[a-zA-Z0-9]+)
```

当请求以下链接时会使用你的解析器：
- ✅ `https://lanzoui.com/i7Aq12ab3cd`
- ✅ `https://lanzoux.com/i7Aq12ab3cd`
- ✅ `http://lanzouy.com/i7Aq12ab3cd`

---

## ⚙️ 高级用法

### 1. 指定解析器类型

```bash
# 通过type参数指定解析器
GET http://localhost:6400/parser?url=https://example.com/s/abc&type=custom_parser
```

### 2. 获取文件列表

对于支持文件夹的网盘：

```bash
# 获取文件列表
GET http://localhost:6400/json/parser/list?url=https://example.com/s/abc

# 按文件ID获取下载链接
GET http://localhost:6400/json/parser/file?url=https://example.com/s/abc&fileId=123
```

### 3. 批量解析

```javascript
const urls = [
  'https://lanzoui.com/i7Aq12ab3cd',
  'https://lanzoui.com/i8Bq34ef5gh'
];

const results = await Promise.all(
  urls.map(url => 
    fetch(`http://localhost:6400/json/parser?url=${encodeURIComponent(url)}`)
      .then(res => res.json())
  )
);
```

---

## 🔒 安全注意事项

### 1. SSRF防护

系统已实施SSRF防护，以下请求会被拦截：

❌ 内网地址：
```bash
# 这些会被拦截
http://127.0.0.1:8080/admin
http://192.168.1.1/config
http://169.254.169.254/latest/meta-data/
```

✅ 公网地址：
```bash
# 这些是允许的
https://lanzoui.com/xxx
https://pan.baidu.com/s/xxx
```

### 2. 速率限制

建议添加速率限制，避免滥用：

```javascript
// 使用节流
import { throttle } from 'lodash';

const parseUrl = throttle((url) => {
  return fetch(`/json/parser?url=${encodeURIComponent(url)}`);
}, 1000); // 每秒最多1次请求
```

---

## 📊 错误处理

### 常见错误码

| 错误码 | 说明 | 解决方法 |
|--------|------|----------|
| 400 | 参数错误 | 检查url参数是否正确编码 |
| 404 | 未找到解析器 | 确认链接格式是否匹配解析器规则 |
| 500 | 解析失败 | 查看日志，可能是解析器代码错误 |
| 503 | 服务不可用 | 稍后重试 |

### 错误响应示例

```json
{
  "code": 500,
  "msg": "解析失败: 无法提取下载参数",
  "data": null
}
```

### 错误处理示例

```javascript
fetch('/json/parser?url=' + encodeURIComponent(shareUrl))
  .then(res => res.json())
  .then(data => {
    if (data.code === 200) {
      console.log('成功:', data.data.url);
    } else {
      console.error('失败:', data.msg);
    }
  })
  .catch(error => {
    console.error('请求失败:', error.message);
  });
```

---

## 💡 最佳实践

### 1. URL编码

始终对分享链接进行URL编码：

```javascript
// ✅ 正确
const encodedUrl = encodeURIComponent('https://lanzoui.com/i7Aq12ab3cd');
fetch(`/json/parser?url=${encodedUrl}`);

// ❌ 错误
fetch('/json/parser?url=https://lanzoui.com/i7Aq12ab3cd');
```

### 2. 错误重试

实现指数退避重试：

```javascript
async function parseWithRetry(url, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      const response = await fetch(`/json/parser?url=${encodeURIComponent(url)}`);
      const data = await response.json();
      
      if (data.code === 200) {
        return data;
      }
      
      // 如果是服务器错误，重试
      if (data.code >= 500 && i < maxRetries - 1) {
        await new Promise(resolve => setTimeout(resolve, Math.pow(2, i) * 1000));
        continue;
      }
      
      throw new Error(data.msg);
    } catch (error) {
      if (i === maxRetries - 1) throw error;
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, i) * 1000));
    }
  }
}
```

### 3. 超时处理

设置请求超时：

```javascript
const controller = new AbortController();
const timeout = setTimeout(() => controller.abort(), 30000); // 30秒超时

fetch('/json/parser?url=' + encodeURIComponent(url), {
  signal: controller.signal
})
  .then(res => res.json())
  .finally(() => clearTimeout(timeout));
```

---

## 📚 更多资源

- **演练场文档**: `/parser/doc/JAVASCRIPT_PARSER_GUIDE.md`
- **自定义解析器**: `/parser/doc/CUSTOM_PARSER_GUIDE.md`
- **安全指南**: `/parser/doc/security/`

---

**最后更新**: 2025-11-29  
**版本**: v1.0

