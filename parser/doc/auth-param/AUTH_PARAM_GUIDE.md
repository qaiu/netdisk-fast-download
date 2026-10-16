# 认证参数传递指南 (Auth Parameter Guide)

## 概述

本文档描述了网盘解析接口中携带认证参数的方法。通过 `auth` 参数，可以在解析请求时传递临时认证信息（如 Cookie、Token、用户名密码等），使解析器能够访问需要登录或授权的网盘资源。

## 网盘认证要求

| 网盘 | 类型代码 | 认证要求 | 说明 |
|------|---------|---------|------|
| 夸克网盘 | QK | **必须** | 必须配置 Cookie 才能解析和下载 |
| UC网盘 | UC | **必须** | 必须配置 Cookie 才能解析和下载 |
| 小飞机网盘 | FJ | 可选 | 大文件（>100MB）需要配置认证信息 |
| 蓝奏优享 | IZ | 可选 | 大文件需要配置认证信息 |
| 其他网盘 | - | 不需要 | 无需认证即可解析 |

> 💡 **如何获取 Cookie**: 在浏览器中登录对应网盘，打开开发者工具（F12），切换到 Network 标签，刷新页面，在请求头中找到 Cookie 字段并复制完整内容。

## 认证参数格式

### 编码流程

```
JSON对象 → AES加密 → Base64编码 → URL编码
```

### 解码流程

```
URL解码 → Base64解码 → AES解密 → JSON对象
```

### 加密配置

- **加密算法**: AES/ECB/PKCS5Padding
- **密钥长度**: 16位（128位）
- **默认密钥**: `nfd_auth_key2026`（可在 `app-dev.yml` 中通过 `server.authEncryptKey` 配置）

### 密钥作用说明（重要）

当前系统中涉及两类不同用途的密钥：

1. `server.authEncryptKey`
   - 用途：加解密 `auth` 参数（前端/调用方传入的认证信息）
   - 影响范围：`/parser`、`/json/parser`、`/v2/linkInfo` 等接口中的 `auth` 参数
   - 注意：这是 **AES 对称加密密钥**，要求 16 位

2. `server.donatedAccountFailureTokenSignKey`
   - 用途：签名和验签“捐赠账号失败计数 token”（用于防伪造、失败计数）
   - 影响范围：捐赠账号失败计数与自动失效逻辑
   - 注意：这是 **HMAC 签名密钥**，与 `authEncryptKey` 已解耦，建议使用高强度随机字符串

> 建议：生产环境务必同时自定义这两个密钥，且不要设置为相同值。

## JSON 模型定义

### AuthParam 对象

```json
{
  "authType": "string",     // 认证类型（必填）
  "username": "string",     // 用户名
  "password": "string",     // 密码
  "token": "string",        // Token/AccessToken/Cookie值
  "cookie": "string",       // Cookie 字符串
  "auth": "string",         // Authorization 头内容
  "ext1": "string",         // 扩展字段1（格式: key:value）
  "ext2": "string",         // 扩展字段2（格式: key:value）
  "ext3": "string",         // 扩展字段3（格式: key:value）
  "ext4": "string",         // 扩展字段4（格式: key:value）
  "ext5": "string"          // 扩展字段5（格式: key:value）
}
```

### 认证类型 (authType)

| authType | 说明 | 主要字段 |
|----------|------|---------|
| `accesstoken` | 使用 AccessToken 认证 | `token` |
| `cookie` | 使用 Cookie 认证 | `token` (存放 cookie 值) |
| `authorization` | 使用 Authorization 头认证 | `token` |
| `password` / `username_password` | 用户名密码认证 | `username`, `password` |
| `custom` | 自定义认证（使用扩展字段） | `token`, `ext1`-`ext5` |

### 示例 JSON

#### 1. Token 认证
```json
{
  "authType": "accesstoken",
  "token": "your_access_token_here"
}
```

#### 2. Cookie 认证
```json
{
  "authType": "cookie",
  "token": "session_id=abc123; user_token=xyz789"
}
```

#### 3. 用户名密码认证
```json
{
  "authType": "password",
  "username": "your_username",
  "password": "your_password"
}
```

> 123云盘（`ye`）账号密码登录如果返回验证码或风控，解析会直接失败，并提示改用 Authorization，不会尝试验证码。请在浏览器登录后复制 `authorToken` / 请求头 `authorization` / Cookie `sso-token`，用 `authType=authorization` 或 `accesstoken` 传入，或写入 `app-dev.yml` 的 `auths.ye.authorization`。

#### 4. 自定义认证
```json
{
  "authType": "custom",
  "token": "main_token",
  "ext1": "refresh_token:your_refresh_token",
  "ext2": "device_id:device123"
}
```

## 接口调用示例

### 基础接口

#### 1. 解析并重定向 (GET /parser)

```
GET /parser?url={分享链接}&pwd={提取码}&auth={加密认证参数}
```

**参数说明:**
- `url`: 网盘分享链接（必填）
- `pwd`: 提取码（可选）
- `auth`: 加密后的认证参数（可选）

**响应:** 302 重定向到直链

#### 2. 解析返回 JSON (GET /json/parser)

```
GET /json/parser?url={分享链接}&pwd={提取码}&auth={加密认证参数}
```

**响应示例:**
```json
{
  "shareKey": "lz:xxxx",
  "directLink": "https://...",
  "cacheHit": false,
  "expires": "2026-02-05 12:00:00",
  "expiration": 1738728000000
}
```

#### 3. 获取链接信息 (GET /v2/linkInfo)

```
GET /v2/linkInfo?url={分享链接}&pwd={提取码}&auth={加密认证参数}
```

**响应:** 返回下载链接、API 链接、预览链接等信息

## 各语言加密示例

### Java

```java
import cn.qaiu.lz.common.util.AuthParamCodec;
import cn.qaiu.lz.web.model.AuthParam;

// 方式1: 使用 AuthParam 对象
AuthParam authParam = AuthParam.builder()
    .authType("accesstoken")
    .token("your_token_here")
    .build();
String encrypted = AuthParamCodec.encode(authParam);

// 方式2: 快速编码
String encrypted = AuthParamCodec.quickEncode("accesstoken", "your_token_here");

// 方式3: 用户名密码
String encrypted = AuthParamCodec.quickEncodePassword("username", "password");

// 解码
AuthParam decoded = AuthParamCodec.decode(encrypted);
```

### JavaScript (浏览器/Node.js)

```javascript
// 使用 CryptoJS 库
const CryptoJS = require('crypto-js');

const AUTH_KEY = 'nfd_auth_key2026';

// 加密
function encodeAuthParam(authObj) {
    const jsonStr = JSON.stringify(authObj);
    const encrypted = CryptoJS.AES.encrypt(jsonStr, CryptoJS.enc.Utf8.parse(AUTH_KEY), {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
    });
    const base64 = encrypted.toString();
    return encodeURIComponent(base64);
}

// 解密
function decodeAuthParam(encryptedAuth) {
    const base64 = decodeURIComponent(encryptedAuth);
    const decrypted = CryptoJS.AES.decrypt(base64, CryptoJS.enc.Utf8.parse(AUTH_KEY), {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
    });
    return JSON.parse(decrypted.toString(CryptoJS.enc.Utf8));
}

// 使用示例
const auth = encodeAuthParam({
    authType: 'accesstoken',
    token: 'your_token_here'
});
const url = `http://127.0.0.1:6400/parser?url=${shareUrl}&auth=${auth}`;
```

### Python

```python
import json
import base64
from urllib.parse import quote, unquote
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

AUTH_KEY = b'nfd_auth_key2026'

def encode_auth_param(auth_obj):
    """加密认证参数"""
    json_str = json.dumps(auth_obj, ensure_ascii=False)
    cipher = AES.new(AUTH_KEY, AES.MODE_ECB)
    padded = pad(json_str.encode('utf-8'), AES.block_size)
    encrypted = cipher.encrypt(padded)
    base64_str = base64.b64encode(encrypted).decode('utf-8')
    return quote(base64_str)

def decode_auth_param(encrypted_auth):
    """解密认证参数"""
    base64_str = unquote(encrypted_auth)
    encrypted = base64.b64decode(base64_str)
    cipher = AES.new(AUTH_KEY, AES.MODE_ECB)
    decrypted = unpad(cipher.decrypt(encrypted), AES.block_size)
    return json.loads(decrypted.decode('utf-8'))

# 使用示例
auth = encode_auth_param({
    'authType': 'accesstoken',
    'token': 'your_token_here'
})
url = f'http://127.0.0.1:6400/parser?url={share_url}&auth={auth}'
```

### cURL 命令行

```bash
# 假设已加密的 auth 参数为 ENCRYPTED_AUTH
curl -L "http://127.0.0.1:6400/parser?url=https://www.lanzoux.com/xxxx&auth=ENCRYPTED_AUTH"

# 获取 JSON 响应
curl "http://127.0.0.1:6400/json/parser?url=https://www.lanzoux.com/xxxx&auth=ENCRYPTED_AUTH"
```

## 解析器使用认证信息

解析器可以从 `shareLinkInfo.otherParam.get("auths")` 获取 MultiMap 格式的认证信息：

```java
// 在解析器中获取认证信息
MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");

if (auths != null) {
    String authType = auths.get("authType");
    String token = auths.get("token");
    String username = auths.get("username");
    String password = auths.get("password");
    
    // 根据 authType 使用相应的认证方式
    switch (authType) {
        case "accesstoken":
            // 使用 token 认证
            break;
        case "password":
            // 使用用户名密码登录
            break;
        // ...
    }
}
```

## 注意事项

1. **安全性**: 
   - 不要在日志中打印完整的认证参数
   - 认证参数通过 HTTPS 传输更安全
   - 密钥应妥善保管，建议在生产环境中更换默认密钥

2. **缓存策略**:
   - 带有临时认证参数的请求目前不会被缓存
   - 每次请求都会重新解析

3. **兼容性**:
   - `auth` 参数与原有的 `pwd` 参数可以同时使用
   - 不提供 `auth` 参数时，使用后台配置的认证信息

4. **扩展字段**:
   - `ext1`-`ext5` 使用 `key:value` 格式
   - 适用于需要传递多个自定义参数的场景

## 配置说明

在 `app-dev.yml` 中配置密钥：

```yaml
server:
  # auth参数加密密钥（16位AES密钥）
  authEncryptKey: 'your_custom_key16'

  # 捐赠账号失败计数token签名密钥（HMAC）
  # 建议使用较长随机字符串，并与 authEncryptKey 不同
  donatedAccountFailureTokenSignKey: 'your_random_hmac_sign_key'
```

### 密钥管理建议

- 不要在公开仓库提交生产密钥
- 建议通过环境变量或私有配置注入
- 调整 `authEncryptKey` 会影响 `auth` 参数兼容性
- 调整 `donatedAccountFailureTokenSignKey` 会使已签发的失败计数 token 失效（短期可接受）

## 更新日志

- **2026-02-05**: 初始版本，支持 accesstoken、cookie、password、custom 认证类型
- **2026-09-27**: 补充 123云盘账号密码登录触发验证码/风控时改用 Authorization 的说明
