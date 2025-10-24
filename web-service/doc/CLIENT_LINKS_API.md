# 客户端下载链接 API 文档

## 概述

新增的客户端下载链接 API 允许用户获取各种下载客户端格式的下载链接，包括 cURL、PowerShell、Aria2、迅雷等。

## API 端点

### 1. 获取所有客户端下载链接

**端点**: `GET /v2/clientLinks`

**参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码

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

### 2. 获取指定类型的客户端下载链接

**端点**: `GET /v2/clientLink`

**参数**:
- `url` (必需): 分享链接
- `pwd` (可选): 提取码
- `clientType` (必需): 客户端类型 (curl, wget, aria2, idm, thunder, bitcomet, motrix, fdm, powershell)

**响应**: 直接返回指定类型的客户端下载链接字符串

## 支持的客户端类型

| 客户端类型 | 代码 | 说明 | 输出格式 |
|-----------|------|------|----------|
| cURL | `curl` | 命令行工具 | curl 命令 |
| wget | `wget` | 命令行工具 | wget 命令 |
| Aria2 | `aria2` | 命令行/RPC | aria2c 命令 |
| IDM | `idm` | Windows 下载管理器 | idm:// 协议链接 |
| 迅雷 | `thunder` | 国内主流下载工具 | thunder:// 协议链接 |
| 比特彗星 | `bitcomet` | BT 下载工具 | bitcomet:// 协议链接 |
| Motrix | `motrix` | 跨平台下载工具 | JSON 格式 |
| FDM | `fdm` | Free Download Manager | 文本格式 |
| PowerShell | `powershell` | Windows PowerShell | PowerShell 命令 |

## 使用示例

### 获取所有客户端链接
```bash
curl "http://localhost:8080/v2/clientLinks?url=https://pan.baidu.com/s/1test123&pwd=1234"
```

### 获取 cURL 命令
```bash
curl "http://localhost:8080/v2/clientLink?url=https://pan.baidu.com/s/1test123&pwd=1234&clientType=curl"
```

### 获取 PowerShell 命令
```bash
curl "http://localhost:8080/v2/clientLink?url=https://pan.baidu.com/s/1test123&pwd=1234&clientType=powershell"
```

## 错误处理

当请求失败时，API 会返回错误信息：

```json
{
  "success": false,
  "error": "解析分享链接失败: 具体错误信息"
}
```

## 注意事项

1. **Referer 支持**: CowTool (奶牛快传) 解析器已正确实现 Referer 请求头支持
2. **请求头处理**: 所有客户端链接都会包含必要的请求头（如 User-Agent、Referer、Cookie 等）
3. **特殊字符转义**: PowerShell 命令会自动转义特殊字符（引号、美元符号等）
4. **异步处理**: API 使用异步处理，确保高性能
5. **错误容错**: 即使某个客户端类型生成失败，其他类型仍会正常生成

## 集成说明

该功能已集成到现有的解析器框架中：

- **ParserApi**: 新增两个 API 端点
- **ClientLinkResp**: 新的响应模型
- **CowTool**: 已支持 Referer 请求头
- **PowerShell**: 新增 PowerShell 格式支持

所有功能都经过测试验证，可以安全使用。
