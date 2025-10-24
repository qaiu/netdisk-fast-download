# 客户端下载链接生成器使用指南

## 概述

客户端下载链接生成器是 parser 模块的新功能，用于将解析得到的直链转换为各种下载客户端可识别的格式，包括 curl、wget、aria2、IDM、迅雷、比特彗星、Motrix、FDM 等主流下载工具。

## 核心特性

- **多客户端支持**：支持 8 种主流下载客户端格式
- **防盗链处理**：自动处理请求头、Referer 等防盗链参数
- **可扩展设计**：支持注册自定义生成器
- **元数据存储**：通过 `ShareLinkInfo.otherParam` 存储下载元数据
- **线程安全**：工厂类使用 ConcurrentHashMap 保证线程安全

## 支持的客户端类型

| 客户端类型 | 代码 | 说明 | 输出格式 |
|-----------|------|------|----------|
| Aria2 | `ARIA2` | 命令行/RPC | aria2c 命令 |
| Motrix | `MOTRIX` | 跨平台下载工具 | JSON 格式 |
| 比特彗星 | `BITCOMET` | BT 下载工具 | bitcomet:// 协议链接 |
| 迅雷 | `THUNDER` | 国内主流下载工具 | thunder:// 协议链接 |
| wget | `WGET` | 命令行工具 | wget 命令 |
| cURL | `CURL` | 命令行工具 | curl 命令 |
| IDM | `IDM` | Windows 下载管理器 | idm:// 协议链接 |
| FDM | `FDM` | Free Download Manager | 文本格式 |
| PowerShell | `POWERSHELL` | Windows PowerShell | PowerShell 命令 |

## 快速开始

### 1. 基本使用

```java
// 解析分享链接
IPanTool tool = ParserCreate.fromShareUrl("https://example.com/share/abc123")
    .createTool();
String directLink = tool.parseSync();

// 获取 ShareLinkInfo
ShareLinkInfo info = tool.getShareLinkInfo();

// 生成所有类型的客户端链接
Map<ClientLinkType, String> clientLinks = ClientLinkGeneratorFactory.generateAll(info);

// 使用生成的链接
String curlCommand = clientLinks.get(ClientLinkType.CURL);
String thunderLink = clientLinks.get(ClientLinkType.THUNDER);
```

### 2. 使用新的便捷方法（推荐）

```java
// 解析分享链接并自动生成客户端链接
IPanTool tool = ParserCreate.fromShareUrl("https://example.com/share/abc123")
    .createTool();

// 一步完成解析和客户端链接生成
Map<ClientLinkType, String> clientLinks = tool.parseWithClientLinksSync();

// 使用生成的链接
String curlCommand = clientLinks.get(ClientLinkType.CURL);
String thunderLink = clientLinks.get(ClientLinkType.THUNDER);
```

### 3. 异步方式

```java
// 异步解析并生成客户端链接
tool.parseWithClientLinks()
    .onSuccess(clientLinks -> {
        log.info("生成的客户端链接: {}", clientLinks);
    })
    .onFailure(error -> {
        log.error("解析失败", error);
    });
```

### 4. 生成特定类型的链接

```java
// 生成 curl 命令
String curlCommand = ClientLinkGeneratorFactory.generate(info, ClientLinkType.CURL);

// 生成迅雷链接
String thunderLink = ClientLinkGeneratorFactory.generate(info, ClientLinkType.THUNDER);

// 生成 aria2 命令
String aria2Command = ClientLinkGeneratorFactory.generate(info, ClientLinkType.ARIA2);
```

### 5. 使用便捷工具类

```java
// 使用 ClientLinkUtils 工具类
String curlCommand = ClientLinkUtils.generateCurlCommand(info);
String wgetCommand = ClientLinkUtils.generateWgetCommand(info);
String thunderLink = ClientLinkUtils.generateThunderLink(info);
String powershellCommand = ClientLinkUtils.generatePowerShellCommand(info);

// 检查是否有有效的下载元数据
boolean hasValidMeta = ClientLinkUtils.hasValidDownloadMeta(info);
```

## 输出示例

### PowerShell 命令示例

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$session.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
Invoke-WebRequest `
-UseBasicParsing `
-Uri "https://example.com/file.zip" `
-WebSession $session `
-Headers @{`
  "Cookie"="session=abc123"`
`
  "Accept"="text/html,application/xhtml+xml"`
`
  "User-Agent"="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"`
`
  "Referer"="https://example.com/share/test"`
} `
-OutFile "test-file.zip"
```

### cURL 命令示例

```bash
curl \
  -L \
  -H \
  "Cookie: session=abc123" \
  -H \
  "User-Agent: Mozilla/5.0 (Test Browser)" \
  -H \
  "Referer: https://example.com/share/test" \
  -o \
  "test-file.zip" \
  "https://example.com/file.zip"
```

### 迅雷链接示例

```
thunder://QUFodHRwczovL2V4YW1wbGUuY29tL2ZpbGUuemlwWlo=
```

### Aria2 命令示例

```bash
aria2c \
--header="Cookie: session=abc123" \
--header="User-Agent: Mozilla/5.0 (Test Browser)" \
--header="Referer: https://example.com/share/test" \
--out="test-file.zip" \
--continue \
--max-tries=3 \
--retry-wait=5 \
"https://example.com/file.zip"
```

## 解析器集成

### 1. 使用 completeWithMeta 方法

在解析器实现中，使用 `PanBase` 提供的 `completeWithMeta` 方法来存储下载元数据：

```java
public class MyPanTool extends PanBase {
    
    @Override
    public Future<String> parse() {
        // ... 解析逻辑 ...
        
        // 获取下载链接
        String downloadUrl = "https://example.com/file.zip";
        
        // 准备请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.put("Referer", shareLinkInfo.getShareUrl());
        headers.put("Cookie", "session=abc123");
        
        // 使用 completeWithMeta 存储元数据
        completeWithMeta(downloadUrl, headers);
        
        return future();
    }
}
```

### 2. 使用 MultiMap 版本

如果使用 Vert.x 的 MultiMap：

```java
MultiMap headers = MultiMap.caseInsensitiveMultiMap();
headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
headers.set("Referer", shareLinkInfo.getShareUrl());

// 使用 MultiMap 版本
completeWithMeta(downloadUrl, headers);
```

## 输出示例

### curl 命令
```bash
curl -L "https://example.com/file.zip" \
  -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
  -H "Referer: https://example.com/share/abc123" \
  -H "Cookie: session=abc123" \
  -o "file.zip"
```

### wget 命令
```bash
wget --header="User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
     --header="Referer: https://example.com/share/abc123" \
     --header="Cookie: session=abc123" \
     -O "file.zip" \
     "https://example.com/file.zip"
```

### aria2 命令
```bash
aria2c --header="User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" \
       --header="Referer: https://example.com/share/abc123" \
       --header="Cookie: session=abc123" \
       --out="file.zip" \
       --continue \
       --max-tries=3 \
       --retry-wait=5 \
       "https://example.com/file.zip"
```

### 迅雷链接
```
thunder://QUFodHRwczovL2V4YW1wbGUuY29tL2ZpbGUuemlwWlo=
```

### IDM 链接
```
idm:///?url=aHR0cHM6Ly9leGFtcGxlLmNvbS9maWxlLnppcA==&header=UmVmZXJlcjogaHR0cHM6Ly9leGFtcGxlLmNvbS9zaGFyZS9hYmMxMjMK
```

## 扩展开发

### 1. 自定义生成器

实现 `ClientLinkGenerator` 接口：

```java
public class MyCustomGenerator implements ClientLinkGenerator {
    
    @Override
    public String generate(DownloadLinkMeta meta) {
        // 自定义生成逻辑
        return "myapp://download?url=" + meta.getUrl();
    }
    
    @Override
    public ClientLinkType getType() {
        return ClientLinkType.CURL; // 或者定义新的类型
    }
}
```

### 2. 注册自定义生成器

```java
// 注册自定义生成器
ClientLinkGeneratorFactory.register(new MyCustomGenerator());

// 使用自定义生成器
String customLink = ClientLinkGeneratorFactory.generate(info, ClientLinkType.CURL);
```

## 注意事项

1. **防盗链处理**：不同网盘的防盗链策略不同，需要在元数据中完整保存所需的 headers
2. **URL 编码**：生成客户端链接时注意 URL 和参数的正确编码（Base64、URLEncode 等）
3. **兼容性**：确保生成的命令/协议在主流客户端中可用
4. **可选特性**：元数据存储和客户端链接生成均为可选，不影响现有解析器功能
5. **线程安全**：工厂类使用 ConcurrentHashMap 存储生成器，支持多线程环境

## API 参考

### IPanTool 接口新增方法

- `parseWithClientLinks()` - 解析文件并生成客户端下载链接（异步）
- `parseWithClientLinksSync()` - 解析文件并生成客户端下载链接（同步）
- `getShareLinkInfo()` - 获取 ShareLinkInfo 对象

### ClientLinkGeneratorFactory

- `generateAll(ShareLinkInfo info)` - 生成所有类型的客户端链接
- `generate(ShareLinkInfo info, ClientLinkType type)` - 生成指定类型的链接
- `register(ClientLinkGenerator generator)` - 注册自定义生成器
- `unregister(ClientLinkType type)` - 注销生成器
- `isRegistered(ClientLinkType type)` - 检查是否已注册

### ClientLinkUtils

- `generateAllClientLinks(ShareLinkInfo info)` - 生成所有客户端链接
- `generateCurlCommand(ShareLinkInfo info)` - 生成 curl 命令
- `generateWgetCommand(ShareLinkInfo info)` - 生成 wget 命令
- `generateThunderLink(ShareLinkInfo info)` - 生成迅雷链接
- `generatePowerShellCommand(ShareLinkInfo info)` - 生成 PowerShell 命令
- `hasValidDownloadMeta(ShareLinkInfo info)` - 检查元数据有效性

### PanBase

- `completeWithMeta(String url, Map<String, String> headers)` - 完成解析并存储元数据
- `completeWithMeta(String url, MultiMap headers)` - 完成解析并存储元数据（MultiMap版本）
