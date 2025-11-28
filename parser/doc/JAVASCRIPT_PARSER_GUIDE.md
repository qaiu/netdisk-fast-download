# JavaScript解析器扩展开发指南

## 概述

本指南介绍如何使用JavaScript编写自定义网盘解析器，支持通过JavaScript代码实现网盘解析逻辑，无需编写Java代码。

## 目录

- [快速开始](#快速开始)
- [API参考](#api参考)
  - [ShareLinkInfo对象](#sharelinkinfo对象)
  - [JsHttpClient对象](#jshttpclient对象)
  - [JsHttpResponse对象](#jshttpresponse对象)
  - [JsLogger对象](#jslogger对象)
- [重定向处理](#重定向处理)
- [代理支持](#代理支持)
- [实现方法](#实现方法)
  - [parse方法（必填）](#parse方法必填)
  - [parseFileList方法（可选）](#parsefilelist方法可选)
  - [parseById方法（可选）](#parsebyid方法可选)
- [错误处理](#错误处理)
- [调试技巧](#调试技巧)
- [最佳实践](#最佳实践)
- [示例解析器](#示例解析器)

## 快速开始

### 1. 创建JavaScript脚本

在 `./custom-parsers/` 目录下创建 `.js` 文件，使用以下模板：

```javascript
// ==UserScript==
// @name         我的解析器
// @type         my_parser
// @displayName  我的网盘
// @description  使用JavaScript实现的网盘解析器
// @match        https?://example\.com/s/(?<KEY>\w+)
// @author       yourname
// @version      1.0.0
// ==/UserScript==

// 使用require导入类型定义（仅用于IDE类型提示）
var types = require('./types');
/** @typedef {types.ShareLinkInfo} ShareLinkInfo */
/** @typedef {types.JsHttpClient} JsHttpClient */
/** @typedef {types.JsLogger} JsLogger */
/** @typedef {types.FileInfo} FileInfo */

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    var url = shareLinkInfo.getShareUrl();
    var response = http.get(url);
    return response.body();
}
```

### 2. 解析器加载路径

JavaScript解析器支持两种加载方式：

#### 内置解析器（jar包内）
- **位置**：jar包内的 `custom-parsers/` 资源目录
- **特点**：随jar包一起发布，无需额外配置
- **路径**：`parser/src/main/resources/custom-parsers/`

#### 外部解析器（用户自定义）
- **默认位置**：应用运行目录下的 `./custom-parsers/` 文件夹
- **配置方式**（优先级从高到低）：
  1. **系统属性**：`-Dparser.custom-parsers.path=/path/to/your/parsers`
  2. **环境变量**：`PARSER_CUSTOM_PARSERS_PATH=/path/to/your/parsers`
  3. **默认路径**：`./custom-parsers/`（相对于应用运行目录）

#### 配置示例

**Maven项目中使用：**
```bash
# 方式1：系统属性
mvn exec:java -Dexec.mainClass="your.MainClass" -Dparser.custom-parsers.path=./src/main/resources/custom-parsers

# 方式2：环境变量
export PARSER_CUSTOM_PARSERS_PATH=./src/main/resources/custom-parsers
mvn exec:java -Dexec.mainClass="your.MainClass"
```

**jar包运行时：**
```bash
# 方式1：系统属性
java -Dparser.custom-parsers.path=/path/to/your/parsers -jar your-app.jar

# 方式2：环境变量
export PARSER_CUSTOM_PARSERS_PATH=/path/to/your/parsers
java -jar your-app.jar
```

**Docker部署：**
```bash
# 挂载外部解析器目录
docker run -d -v /path/to/your/parsers:/app/custom-parsers your-image

# 或使用环境变量
docker run -d -e PARSER_CUSTOM_PARSERS_PATH=/app/custom-parsers your-image
```

### 3. 重启应用

重启应用后，JavaScript解析器会自动加载并注册。查看应用日志确认解析器是否成功加载。

## 元数据格式

### 必填字段

- `@name`: 脚本名称
- `@type`: 解析器类型标识（唯一）
- `@displayName`: 显示名称
- `@match`: URL匹配正则（必须包含 `(?<KEY>...)` 命名捕获组）

### 可选字段

- `@description`: 描述信息
- `@author`: 作者
- `@version`: 版本号

### 示例

```javascript
// ==UserScript==
// @name         蓝奏云解析器
// @type         lanzou_js
// @displayName  蓝奏云(JS)
// @description  使用JavaScript实现的蓝奏云解析器
// @match        https?://.*\.lanzou[a-z]\.com/(?<KEY>\w+)
// @match        https?://.*\.lanzoui\.com/(?<KEY>\w+)
// @author       qaiu
// @version      1.0.0
// ==/UserScript==
```

## API参考

### ShareLinkInfo对象

提供分享链接信息的访问接口：

```javascript
// 获取分享URL
var shareUrl = shareLinkInfo.getShareUrl();

// 获取分享Key
var shareKey = shareLinkInfo.getShareKey();

// 获取分享密码
var password = shareLinkInfo.getSharePassword();

// 获取网盘类型
var type = shareLinkInfo.getType();

// 获取网盘名称
var panName = shareLinkInfo.getPanName();

// 获取其他参数
var dirId = shareLinkInfo.getOtherParam("dirId");
var paramJson = shareLinkInfo.getOtherParam("paramJson");

// 检查参数是否存在
if (shareLinkInfo.hasOtherParam("customParam")) {
    var value = shareLinkInfo.getOtherParamAsString("customParam");
}
```

### JsHttpClient对象

提供HTTP请求功能：

```javascript
// GET请求
var response = http.get("https://api.example.com/data");

// GET请求并跟随重定向
var redirectResponse = http.getWithRedirect("https://api.example.com/redirect");

// GET请求但不跟随重定向（用于获取Location头）
var noRedirectResponse = http.getNoRedirect("https://api.example.com/redirect");
if (noRedirectResponse.statusCode() >= 300 && noRedirectResponse.statusCode() < 400) {
    var location = noRedirectResponse.header("Location");
    console.log("重定向到: " + location);
}

// POST请求
var response = http.post("https://api.example.com/submit", {
    key: "value",
    data: "test"
});

// 设置请求头（单个）
http.putHeader("User-Agent", "MyBot/1.0")
    .putHeader("Authorization", "Bearer token");

// 批量设置请求头
http.putHeaders({
    "User-Agent": "MyBot/1.0",
    "Authorization": "Bearer token",
    "Accept": "application/json"
});

// 删除指定请求头
http.removeHeader("Authorization");

// 清空所有请求头（保留默认头）
http.clearHeaders();

// 获取所有请求头
var allHeaders = http.getHeaders();
logger.debug("当前请求头: " + JSON.stringify(allHeaders));

// 设置请求超时时间（秒）
http.setTimeout(60); // 设置为60秒

// PUT请求
var putResponse = http.put("https://api.example.com/resource", {
    key: "value"
});

// DELETE请求
var deleteResponse = http.delete("https://api.example.com/resource/123");

// PATCH请求
var patchResponse = http.patch("https://api.example.com/resource/123", {
    key: "newValue"
});

// URL编码/解码（静态方法）
var encoded = JsHttpClient.urlEncode("hello world"); // "hello%20world"
var decoded = JsHttpClient.urlDecode("hello%20world"); // "hello world"

// 发送简单表单数据
var formResponse = http.sendForm({
    username: "user",
    password: "pass"
});

// 发送JSON数据
var jsonResponse = http.sendJson({
    name: "test",
    value: 123
});
```

### JsHttpResponse对象

处理HTTP响应：

```javascript
var response = http.get("https://api.example.com/data");

// 获取响应体（字符串）
var body = response.body();

// 解析JSON响应
var data = response.json();

// 获取状态码
var status = response.statusCode();

// 获取响应头
var contentType = response.header("Content-Type");
var allHeaders = response.headers();

// 检查请求是否成功
if (response.isSuccess()) {
    logger.info("请求成功");
} else {
    logger.error("请求失败: " + status);
}

// 获取响应体字节数组
var bytes = response.bodyBytes();

// 获取响应体大小
var size = response.bodySize();
logger.info("响应体大小: " + size + " 字节");
```

### JsLogger对象

提供日志功能：

```javascript
// 不同级别的日志
logger.debug("调试信息");
logger.info("一般信息");
logger.warn("警告信息");
logger.error("错误信息");

// 带参数的日志
logger.info("用户 {} 访问了 {}", username, url);

// 检查日志级别
if (logger.isDebugEnabled()) {
    logger.debug("详细的调试信息");
}
```

## 重定向处理

当网盘服务返回302重定向时，可以使用`getNoRedirect`方法获取真实的下载链接：

```javascript
/**
 * 获取真实的下载链接（处理302重定向）
 * @param {string} downloadUrl - 原始下载链接
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 真实的下载链接
 */
function getRealDownloadUrl(downloadUrl, http, logger) {
    try {
        logger.info("获取真实下载链接: " + downloadUrl);
        
        // 使用不跟随重定向的方法获取Location头
        var headResponse = http.getNoRedirect(downloadUrl);
        
        if (headResponse.statusCode() >= 300 && headResponse.statusCode() < 400) {
            // 处理重定向
            var location = headResponse.header("Location");
            if (location) {
                logger.info("获取到重定向链接: " + location);
                return location;
            }
        }
        
        // 如果没有重定向或无法获取Location，返回原链接
        logger.debug("下载链接无需重定向或无法获取重定向信息");
        return downloadUrl;
        
    } catch (e) {
        logger.error("获取真实下载链接失败: " + e.message);
        // 如果获取失败，返回原链接
        return downloadUrl;
    }
}

// 在parse方法中使用
function parse(shareLinkInfo, http, logger) {
    // ... 获取原始下载链接的代码 ...
    var originalUrl = "https://example.com/download?id=123";
    
    // 获取真实的下载链接
    var realUrl = getRealDownloadUrl(originalUrl, http, logger);
    return realUrl;
}
```

## 代理支持

JavaScript解析器支持HTTP代理配置，代理信息通过`ShareLinkInfo`的`otherParam`传递：

```javascript
function parse(shareLinkInfo, http, logger) {
    // 检查是否有代理配置
    var proxyConfig = shareLinkInfo.getOtherParam("proxy");
    if (proxyConfig) {
        logger.info("使用代理: " + proxyConfig.host + ":" + proxyConfig.port);
    }
    
    // HTTP客户端会自动使用代理配置
    var response = http.get("https://api.example.com/data");
    return response.body();
}
```

代理配置格式：
```json
{
    "type": "HTTP",  // 代理类型: HTTP, SOCKS4, SOCKS5
    "host": "proxy.example.com",
    "port": 8080,
    "username": "user",  // 可选，代理认证用户名
    "password": "pass"    // 可选，代理认证密码
}
```

## 实现方法

JavaScript解析器支持三种方法，对应Java接口的三种同步方法：

### parse方法（必填）

解析单个文件的下载链接，对应Java的 `parseSync()` 方法：

```javascript
function parse(shareLinkInfo, http, logger) {
    var shareUrl = shareLinkInfo.getShareUrl();
    var password = shareLinkInfo.getSharePassword();
    
    // 发起请求获取页面
    var response = http.get(shareUrl);
    var html = response.body();
    
    // 解析HTML获取下载链接
    var regex = /downloadUrl["']:\s*["']([^"']+)["']/;
    var match = html.match(regex);
    
    if (match) {
        return match[1]; // 返回下载链接
    } else {
        throw new Error("无法解析下载链接");
    }
}
```

### parseFileList方法（可选）

解析文件列表（目录），对应Java的 `parseFileListSync()` 方法：

```javascript
function parseFileList(shareLinkInfo, http, logger) {
    var dirId = shareLinkInfo.getOtherParam("dirId") || "0";
    
    // 请求文件列表API
    var response = http.get("/api/list?dirId=" + dirId);
    var data = response.json();
    
    var fileList = [];
    for (var i = 0; i < data.files.length; i++) {
        var file = data.files[i];
        
        var fileInfo = {
            fileName: file.name,
            fileId: file.id,
            fileType: file.isDir ? "folder" : "file",
            size: file.size,
            sizeStr: formatSize(file.size),
            createTime: file.createTime,
            parserUrl: "/v2/redirectUrl/my_parser/" + file.id
        };
        
        fileList.push(fileInfo);
    }
    
    return fileList;
}
```

### parseById方法（可选）

根据文件ID获取下载链接，对应Java的 `parseByIdSync()` 方法：

```javascript
function parseById(shareLinkInfo, http, logger) {
    var paramJson = shareLinkInfo.getOtherParam("paramJson");
    var fileId = paramJson.fileId;
    
    // 请求下载API
    var response = http.get("/api/download?fileId=" + fileId);
    var data = response.json();
    
    return data.downloadUrl;
}
```

## 同步方法支持

JavaScript解析器的方法都是同步执行的，对应Java接口的三种同步方法：

### 方法对应关系

| JavaScript方法 | Java同步方法 | 说明 |
|----------------|-------------|------|
| `parse()` | `parseSync()` | 解析单个文件下载链接 |
| `parseFileList()` | `parseFileListSync()` | 解析文件列表 |
| `parseById()` | `parseByIdSync()` | 根据文件ID获取下载链接 |

### 使用示例

```javascript
// 在Java中调用JavaScript解析器
IPanTool tool = ParserCreate.fromType("my_js_parser")
    .shareKey("abc123")
    .createTool();

// 使用同步方法调用JavaScript函数
String downloadUrl = tool.parseSync();           // 调用 parse() 函数
List<FileInfo> files = tool.parseFileListSync(); // 调用 parseFileList() 函数  
String fileUrl = tool.parseByIdSync();           // 调用 parseById() 函数
```

### 注意事项

- JavaScript方法都是同步执行的，无需处理异步回调
- 如果JavaScript方法抛出异常，Java同步方法会抛出相应的异常
- 建议在JavaScript方法中添加适当的错误处理和日志记录

## 函数定义方式

JavaScript解析器使用全局函数定义，不需要`exports`对象：

```javascript
/**
 * 解析单个文件下载链接（必填）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    // 实现解析逻辑
    return "https://example.com/download";
}

/**
 * 解析文件列表（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {Array} 文件信息数组
 */
function parseFileList(shareLinkInfo, http, logger) {
    // 实现文件列表解析逻辑
    return [];
}

/**
 * 根据文件ID获取下载链接（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parseById(shareLinkInfo, http, logger) {
    // 实现按ID解析逻辑
    return "https://example.com/download";
}
```

**注意**：JavaScript解析器通过`engine.eval()`执行，函数必须定义为全局函数，不需要使用`exports`或`module.exports`。

## VSCode配置

### 1. 安装JavaScript扩展

安装 "JavaScript (ES6) code snippets" 扩展。

### 2. 配置jsconfig.json

在 `custom-parsers` 目录下创建 `jsconfig.json`：

```json
{
  "compilerOptions": {
    "checkJs": true,
    "target": "ES5",
    "lib": ["ES5"],
    "allowJs": true,
    "noEmit": true
  },
  "include": ["*.js", "types.d.ts"],
  "exclude": ["node_modules"]
}
```

### 3. 使用类型提示

```javascript
// 引用类型定义
var types = require('./types');
/** @typedef {types.ShareLinkInfo} ShareLinkInfo */
/** @typedef {types.JsHttpClient} JsHttpClient */

// 使用类型注解
/**
 * @param {ShareLinkInfo} shareLinkInfo
 * @param {JsHttpClient} http
 * @returns {string}
 */
function parse(shareLinkInfo, http, logger) {
    // VSCode会提供代码补全和类型检查
}
```

## 调试技巧

### 1. 使用日志

```javascript
function parse(shareLinkInfo, http, logger) {
    logger.info("开始解析: " + shareLinkInfo.getShareUrl());
    
    var response = http.get(shareLinkInfo.getShareUrl());
    logger.debug("响应状态: " + response.statusCode());
    logger.debug("响应内容: " + response.body().substring(0, 100));
    
    // 解析逻辑...
}
```

### 2. 错误处理

```javascript
function parse(shareLinkInfo, http, logger) {
    try {
        var response = http.get(shareLinkInfo.getShareUrl());
        
        if (!response.isSuccess()) {
            throw new Error("HTTP请求失败: " + response.statusCode());
        }
        
        var data = response.json();
        return data.downloadUrl;
        
    } catch (e) {
        logger.error("解析失败: " + e.message);
        throw e; // 重新抛出异常
    }
}
```

### 3. 启用调试模式

设置系统属性启用详细日志：

```bash
-Dnfd.js.debug=true
```

## 常见问题

### Q: 如何获取分享密码？

A: 使用 `shareLinkInfo.getSharePassword()` 方法。

### Q: 如何处理需要登录的网盘？

A: 使用 `http.putHeader()` 设置认证头，或使用 `http.sendForm()` 发送登录表单。

### Q: 如何解析复杂的HTML？

A: 使用正则表达式或字符串方法解析HTML内容。

### Q: 如何处理异步请求？

A: 当前版本使用同步API，所有HTTP请求都是同步的。

### Q: 如何调试JavaScript代码？

A: 使用 `logger.debug()` 输出调试信息，查看应用日志。

### Q: 如何批量设置请求头？

A: 使用 `http.putHeaders()` 方法批量设置多个请求头：

```javascript
// 批量设置请求头
http.putHeaders({
    "User-Agent": "Mozilla/5.0...",
    "Accept": "application/json",
    "Authorization": "Bearer token",
    "Referer": "https://example.com"
});
```

### Q: 如何清空所有请求头？

A: 使用 `http.clearHeaders()` 方法清空所有请求头（会保留默认头）：

```javascript
// 清空所有请求头，保留默认头（Accept-Encoding、User-Agent、Accept-Language）
http.clearHeaders();
```

### Q: 如何设置请求超时时间？

A: 使用 `http.setTimeout()` 方法设置超时时间（秒）：

```javascript
// 设置超时时间为60秒
http.setTimeout(60);
var response = http.get("https://api.example.com/data");
```

## 示例脚本

参考以下示例文件，包含完整的解析器实现：

- **`parser/src/main/resources/custom-parsers/example-demo.js`** - 完整的演示解析器，展示所有功能
- **`parser/src/main/resources/custom-parsers/baidu-photo.js`** - 百度相册解析器示例
- **`parser/src/main/resources/custom-parsers/migu-music.js`** - 咪咕音乐解析器示例
- **`parser/src/main/resources/custom-parsers/qishui-music.js`** - 汽水音乐解析器示例

这些示例展示了：
- 元数据配置
- 三个核心方法的实现（parse、parseFileList、parseById）
- 错误处理和日志记录
- 文件信息构建
- 重定向处理
- 代理支持
- Header管理（批量设置、清空等）

## 限制说明

1. **JavaScript版本**: 仅支持ES5.1语法（Nashorn引擎限制）
2. **同步执行**: 所有HTTP请求都是同步的
3. **内存限制**: 长时间运行可能存在内存泄漏风险
4. **安全限制**: 无法访问文件系统或执行系统命令

## 相关文档

- [自定义解析器扩展指南](CUSTOM_PARSER_GUIDE.md) - Java自定义解析器扩展
- [自定义解析器快速开始](CUSTOM_PARSER_QUICKSTART.md) - 快速上手指南
- [解析器开发文档](README.md) - 解析器开发约定和规范

## 更新日志

- v1.0.0: 初始版本，支持基本的JavaScript解析器功能
- 支持外部解析器路径配置（系统属性、环境变量）
- 支持重定向处理（getNoRedirect、getWithRedirect）
- 支持代理配置（HTTP/SOCKS4/SOCKS5）
- v1.1.0: 增强HTTP客户端功能
  - 新增header管理方法：clearHeaders、removeHeader、putHeaders、getHeaders
  - 新增HTTP请求方法：PUT、DELETE、PATCH
  - 新增工具方法：URL编码/解码（urlEncode、urlDecode）
  - 新增超时时间设置：setTimeout
  - 响应对象增强：bodyBytes、bodySize
