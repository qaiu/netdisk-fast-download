# 网盘快速下载服务 API 文档

## 概述

本文档描述了网盘快速下载服务的所有 REST API 接口。该服务支持多种网盘的分享链接解析，提供直链下载、预览、客户端下载链接等功能。

**基础URL**: `http://localhost:6400` (根据实际部署情况调整)

---

## 目录

- [解析相关接口](#解析相关接口)
- [文件列表接口](#文件列表接口)
- [预览接口](#预览接口)
- [客户端下载链接接口](#客户端下载链接接口)
- [统计信息接口](#统计信息接口)
- [网盘列表接口](#网盘列表接口)
- [版本信息接口](#版本信息接口)
- [隔空喊话接口](#隔空喊话接口)
- [快捷下载接口](#快捷下载接口)

---

## 解析相关接口

### 1. 解析分享链接（重定向）

**接口**: `GET /parser`

**描述**: 解析分享链接并重定向到直链下载地址

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码

**请求示例**:
```
GET /parser?url=https://pan.baidu.com/s/1test123&pwd=1234
```

**响应**: 
- 302 重定向到直链下载地址
- 响应头包含:
  - `nfd-cache-hit`: 是否命中缓存 (true/false)
  - `nfd-cache-expires`: 缓存过期时间

---

### 2. 解析分享链接（JSON）

**接口**: `GET /json/parser`

**描述**: 解析分享链接并返回JSON格式的直链信息

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码

**请求示例**:
```
GET /json/parser?url=https://pan.baidu.com/s/1test123&pwd=1234
```

**响应示例**:
```json
{
  "shareKey": "pan:1test123",
  "directLink": "https://example.com/download/file.zip",
  "cacheHit": false,
  "expires": "2025-01-22 12:00:00",
  "expiration": 86400000,
  "fileInfo": {
    "fileName": "file.zip",
    "fileId": "123456",
    "size": 1024000,
    "sizeStr": "1MB",
    "fileType": "zip",
    "createTime": "2025-01-21 10:00:00"
  }
}
```

---

### 3. 根据类型和Key解析（重定向）

**接口**: `GET /:type/:key`

**描述**: 根据网盘类型和分享Key解析并重定向到直链

**路径参数**:
- `type` (必需): 网盘类型标识（如: lz, pan, cow等）
- `key` (必需): 分享Key，如果包含提取码，格式为 `key@pwd`

**请求示例**:
```
GET /lz/ia2cntg
GET /lz/icBp6qqj82b@QAIU
```

**响应**: 302 重定向到直链下载地址

---

### 4. 根据类型和Key解析（JSON）

**接口**: `GET /json/:type/:key`

**描述**: 根据网盘类型和分享Key解析并返回JSON格式的直链信息

**路径参数**:
- `type` (必需): 网盘类型标识
- `key` (必需): 分享Key，如果包含提取码，格式为 `key@pwd`

**请求示例**:
```
GET /json/lz/ia2cntg
GET /json/lz/icBp6qqj82b@QAIU
```

**响应格式**: 同 `/json/parser`

---

### 5. 获取链接信息（V2）

**接口**: `GET /v2/linkInfo`

**描述**: 获取分享链接的详细信息，包括下载链接、预览链接、统计信息等

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码

**请求示例**:
```
GET /v2/linkInfo?url=https://pan.baidu.com/s/1test123&pwd=1234
```

**响应示例**:
```json
{
  "downLink": "http://127.0.0.1:6400/d/pan/1test123",
  "apiLink": "http://127.0.0.1:6400/json/pan/1test123",
  "viewLink": "http://127.0.0.1:6400/v2/view/pan/1test123",
  "cacheHitTotal": 10,
  "parserTotal": 5,
  "sumTotal": 15,
  "shareLinkInfo": {
    "shareKey": "1test123",
    "panName": "百度网盘",
    "type": "pan",
    "sharePassword": "1234",
    "shareUrl": "https://pan.baidu.com/s/1test123",
    "standardUrl": "https://pan.baidu.com/s/1test123",
    "otherParam": {}
  }
}
```

---

## 文件列表接口

### 6. 获取文件列表

**接口**: `GET /v2/getFileList`

**描述**: 获取分享链接中的文件列表（适用于目录分享）

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码
- `dirId` (可选): 目录ID，用于获取指定目录下的文件
- `uuid` (可选): UUID，某些网盘需要此参数

**请求示例**:
```
GET /v2/getFileList?url=https://pan.baidu.com/s/1test123&pwd=1234&dirId=dir123
```

**响应示例**:
```json
[
  {
    "fileName": "file1.zip",
    "fileId": "file123",
    "size": 1024000,
    "sizeStr": "1MB",
    "fileType": "zip",
    "filePath": "/folder/file1.zip",
    "createTime": "2025-01-21 10:00:00"
  },
  {
    "fileName": "file2.pdf",
    "fileId": "file456",
    "size": 2048000,
    "sizeStr": "2MB",
    "fileType": "pdf",
    "filePath": "/folder/file2.pdf",
    "createTime": "2025-01-21 11:00:00"
  }
]
```

---

## 预览接口

### 7. 预览媒体文件（按类型和Key）

**接口**: `GET /v2/view/:type/:key`

**描述**: 预览指定类型和Key的媒体文件（图片、视频等）

**路径参数**:
- `type` (必需): 网盘类型标识
- `key` (必需): 分享Key，如果包含提取码，格式为 `key@pwd`

**请求示例**:
```
GET /v2/view/pan/1test123
GET /v2/view/lz/ia2cntg@QAIU
```

**响应**: 302 重定向到预览页面

**特殊说明**: 
- WPS网盘类型(pwps)会直接重定向到原分享链接（WPS支持在线预览）

---

### 8. 预览媒体文件（按URL）

**接口**: `GET /v2/preview`

**描述**: 通过分享链接预览媒体文件

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码

**请求示例**:
```
GET /v2/preview?url=https://pan.baidu.com/s/1test123&pwd=1234
```

**响应**: 302 重定向到预览页面

**特殊说明**: 
- WPS网盘类型会直接重定向到原分享链接

---

### 9. 预览URL（目录预览）

**接口**: `GET /v2/viewUrl/:type/:param`

**描述**: 预览目录中的文件，param为Base64编码的参数

**路径参数**:
- `type` (必需): 网盘类型标识
- `param` (必需): Base64编码的参数JSON

**请求示例**:
```
GET /v2/viewUrl/pan/eyJmaWxlSWQiOiIxMjM0NTYifQ==
```

**响应**: 302 重定向到预览页面

---

## 客户端下载链接接口

### 10. 获取所有客户端下载链接

**接口**: `GET /v2/clientLinks`

**描述**: 获取所有支持的客户端格式的下载链接

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码

**请求示例**:
```
GET /v2/clientLinks?url=https://pan.baidu.com/s/1test123&pwd=1234
```

**响应示例**:
```json
{
  "success": true,
  "directLink": "https://example.com/file.zip",
  "fileName": "test-file.zip",
  "fileSize": 1024000,
  "clientLinks": {
    "CURL": "curl -L -H \"User-Agent: Mozilla/5.0...\" -o \"test-file.zip\" \"https://example.com/file.zip\"",
    "POWERSHELL": "$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession...",
    "ARIA2": "aria2c --header=\"User-Agent: Mozilla/5.0...\" --out=\"test-file.zip\" \"https://example.com/file.zip\"",
    "THUNDER": "thunder://QUFodHRwczovL2V4YW1wbGUuY29tL2ZpbGUuemlwWlo=",
    "IDM": "idm://https://example.com/file.zip",
    "WGET": "wget --header=\"User-Agent: Mozilla/5.0...\" -O \"test-file.zip\" \"https://example.com/file.zip\"",
    "BITCOMET": "bitcomet://https://example.com/file.zip",
    "MOTRIX": "{\"url\":\"https://example.com/file.zip\",\"out\":\"test-file.zip\"}",
    "FDM": "https://example.com/file.zip"
  },
  "supportedClients": {
    "curl": "cURL 命令",
    "wget": "wget 命令",
    "aria2": "Aria2",
    "idm": "IDM",
    "thunder": "迅雷",
    "bitcomet": "比特彗星",
    "motrix": "Motrix",
    "fdm": "Free Download Manager",
    "powershell": "PowerShell"
  },
  "parserInfo": "百度网盘 - pan"
}
```

**支持的客户端类型**:
- `curl`: cURL 命令
- `wget`: wget 命令
- `aria2`: Aria2
- `idm`: IDM
- `thunder`: 迅雷
- `bitcomet`: 比特彗星
- `motrix`: Motrix
- `fdm`: Free Download Manager
- `powershell`: PowerShell

---

### 11. 获取指定类型的客户端下载链接

**接口**: `GET /v2/clientLink`

**描述**: 获取指定客户端类型的下载链接

**请求参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码
- `clientType` (必需): 客户端类型 (curl, wget, aria2, idm, thunder, bitcomet, motrix, fdm, powershell)

**请求示例**:
```
GET /v2/clientLink?url=https://pan.baidu.com/s/1test123&pwd=1234&clientType=curl
```

**响应**: 直接返回指定类型的客户端下载链接字符串

**响应示例**:
```
curl -L -H "User-Agent: Mozilla/5.0..." -o "test-file.zip" "https://example.com/file.zip"
```

---

## 统计信息接口

### 12. 获取统计信息

**接口**: `GET /v2/statisticsInfo`

**描述**: 获取系统统计信息，包括解析总数、缓存总数等

**请求示例**:
```
GET /v2/statisticsInfo
```

**响应示例**:
```json
{
  "parserTotal": 1000,
  "cacheTotal": 500,
  "total": 1500
}
```

---

## 网盘列表接口

### 13. 获取支持的网盘列表

**接口**: `GET /v2/getPanList`

**描述**: 获取所有支持的网盘列表及其信息

**请求示例**:
```
GET /v2/getPanList
```

**响应示例**:
```json
[
  {
    "name": "蓝奏云",
    "type": "lz",
    "shareUrlFormat": "https://www.lanzou*.com/s/{shareKey}",
    "url": "https://www.lanzou.com"
  },
  {
    "name": "百度网盘",
    "type": "pan",
    "shareUrlFormat": "https://pan.baidu.com/s/{shareKey}",
    "url": "https://pan.baidu.com"
  }
]
```

---

## 版本信息接口

### 14. 获取版本号

**接口**: `GET /v2/build-version`

**描述**: 获取应用版本号

**请求示例**:
```
GET /v2/build-version
```

**响应**: 版本号字符串

**响应示例**:
```
20250121_101530
```

---

## 隔空喊话接口

### 15. 提交消息

**接口**: `POST /v2/shout/submit`

**描述**: 提交一条隔空喊话消息，返回6位提取码

**请求体**:
```json
{
  "content": "这是一条消息内容"
}
```

**请求示例**:
```
POST /v2/shout/submit
Content-Type: application/json

{
  "content": "Hello World!"
}
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "success": true,
  "data": "123456",
  "timestamp": 1705896000000
}
```

**说明**: 
- `data` 字段为6位提取码，用于后续提取消息
- 内容不能为空

---

### 16. 检索消息

**接口**: `GET /v2/shout/retrieve`

**描述**: 根据提取码检索消息

**请求参数**:
- `code` (必需): 6位提取码

**请求示例**:
```
GET /v2/shout/retrieve?code=123456
```

**响应示例**:
```json
{
  "id": 1,
  "code": "123456",
  "content": "Hello World!",
  "ip": "127.0.0.1",
  "createTime": "2025-01-21 10:00:00",
  "expireTime": "2025-01-22 10:00:00",
  "isUsed": false
}
```

**错误响应**:
- 如果提取码格式不正确（不是6位数字），返回错误信息

---

## 快捷下载接口

### 17. 下载重定向（短链）

**接口**: `GET /d/:type/:key`

**描述**: 短链形式的下载重定向，等同于 `/:type/:key`

**路径参数**:
- `type` (必需): 网盘类型标识
- `key` (必需): 分享Key，如果包含提取码，格式为 `key@pwd`

**请求示例**:
```
GET /d/lz/ia2cntg
```

**响应**: 302 重定向到直链下载地址

---

### 18. 重定向下载URL（目录文件）

**接口**: `GET /v2/redirectUrl/:type/:param`

**描述**: 重定向到目录中指定文件的下载地址，param为Base64编码的参数

**路径参数**:
- `type` (必需): 网盘类型标识
- `param` (必需): Base64编码的参数JSON

**请求示例**:
```
GET /v2/redirectUrl/pan/eyJmaWxlSWQiOiIxMjM0NTYifQ==
```

**响应**: 302 重定向到直链下载地址

---

## 错误处理

所有接口在发生错误时，会返回JSON格式的错误信息：

```json
{
  "code": 500,
  "msg": "错误描述信息",
  "success": false,
  "data": null,
  "timestamp": 1705896000000
}
```

常见错误：
- 参数缺失或格式错误
- 分享链接无效或已过期
- 提取码错误
- 网盘类型不支持
- 服务器内部错误

---

## 注意事项

1. **缓存机制**: 系统会对解析结果进行缓存，响应头中包含缓存相关信息
2. **User-Agent**: 某些网盘需要特定的User-Agent，系统会自动处理
3. **Referer**: 某些网盘（如奶牛快传）需要Referer请求头
4. **提取码格式**: 在路径参数中，提取码使用 `@` 符号分隔，如 `key@pwd`
5. **Base64参数**: 目录相关接口的param参数需要Base64编码
6. **WPS特殊处理**: WPS网盘类型在预览时会直接使用原分享链接

---

## 支持的网盘类型

系统支持多种网盘，包括但不限于：
- 蓝奏云 (lz)
- 百度网盘 (pan)
- 奶牛快传 (cow)
- 123网盘 (ye)
- 移动云空间 (ec)
- 小飞机盘 (fj)
- 360亿方云 (fc)
- 联想乐云 (le)
- 文叔叔 (ws)
- Cloudreve (ce)
- 等等...

完整列表可通过 `/v2/getPanList` 接口获取。

---

## 更新日志

- 2025-01-21: 初始版本文档
- 支持客户端下载链接功能
- 支持隔空喊话功能


