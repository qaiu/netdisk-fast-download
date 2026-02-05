# 认证参数传递指南 (简化版)

## JSON 对象模型

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

## 认证类型

| authType | 说明 | 主要字段 |
|----------|------|---------|
| `accesstoken` | AccessToken 认证 | `token` |
| `cookie` | Cookie 认证 | `token` |
| `authorization` | Authorization 头认证 | `token` |
| `password` | 用户名密码认证 | `username`, `password` |
| `custom` | 自定义认证 | `token`, `ext1`-`ext5` |

## 示例

### Token 认证
```json
{
  "authType": "accesstoken",
  "token": "your_access_token_here"
}
```

### Cookie 认证
```json
{
  "authType": "cookie",
  "token": "session_id=abc123; user_token=xyz789"
}
```

### 用户名密码
```json
{
  "authType": "password",
  "username": "your_username",
  "password": "your_password"
}
```

### 自定义认证
```json
{
  "authType": "custom",
  "token": "main_token",
  "ext1": "refresh_token:your_refresh_token",
  "ext2": "device_id:device123"
}
```

## 使用说明

1. **编码流程**: JSON对象 → AES加密 → Base64编码 → URL编码
2. **加密配置**: AES/ECB/PKCS5Padding, 密钥: `nfd_auth_key2026` (16位)
3. **接口调用**: `GET /parser?url={分享链接}&pwd={提取码}&auth={加密认证参数}`



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