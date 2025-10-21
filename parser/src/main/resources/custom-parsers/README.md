# JavaScript解析器扩展使用指南

## 概述

本项目支持用户使用JavaScript编写自定义网盘解析器，提供灵活的扩展能力。JavaScript解析器运行在Nashorn引擎中，支持ES5.1语法。

## 文件结构

```
custom-parsers/
├── types.js          # 类型定义文件（JSDoc注释）
├── jsconfig.json     # VSCode配置文件
├── example-demo.js   # 示例解析器
└── README.md         # 本说明文档
```

## 快速开始

### 1. 创建解析器脚本

在 `custom-parsers/` 目录下创建 `.js` 文件，使用以下格式：

```javascript
// ==UserScript==
// @name         你的解析器名称
// @type         解析器类型标识
// @displayName  显示名称
// @description  解析器描述
// @match        匹配URL的正则表达式
// @author       作者
// @version      版本号
// ==/UserScript==

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    // 你的解析逻辑
    return "https://example.com/download/file.zip";
}

/**
 * 解析文件列表（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {FileInfo[]} 文件信息列表
 */
function parseFileList(shareLinkInfo, http, logger) {
    // 你的文件列表解析逻辑
    return [];
}

/**
 * 根据文件ID获取下载链接（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 下载链接
 */
function parseById(shareLinkInfo, http, logger) {
    // 你的按ID解析逻辑
    return "https://example.com/download/" + fileId;
}
```

### 2. 自动加载

解析器会在应用启动时自动加载和注册。支持两种加载方式：

#### 内置解析器（jar包内）
- 位置：jar包内的 `custom-parsers/` 资源目录
- 特点：随jar包一起发布，无需额外配置

#### 外部解析器（用户自定义）
- 默认位置：应用运行目录下的 `./custom-parsers/` 文件夹
- 配置方式：
  - **系统属性**：`-Dparser.custom-parsers.path=/path/to/your/parsers`
  - **环境变量**：`PARSER_CUSTOM_PARSERS_PATH=/path/to/your/parsers`
  - **默认路径**：`./custom-parsers/`（相对于应用运行目录）

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

## API参考

### ShareLinkInfo

分享链接信息对象：

```javascript
shareLinkInfo.getShareUrl()      // 获取分享URL
shareLinkInfo.getShareKey()      // 获取分享Key
shareLinkInfo.getSharePassword() // 获取分享密码
shareLinkInfo.getType()          // 获取网盘类型
shareLinkInfo.getPanName()       // 获取网盘名称
shareLinkInfo.getOtherParam(key) // 获取其他参数
```

### JsHttpClient

HTTP客户端对象：

```javascript
http.get(url)                           // GET请求
http.post(url, data)                     // POST请求
http.putHeader(name, value)              // 设置请求头
http.sendForm(data)                      // 发送表单数据
http.sendJson(data)                      // 发送JSON数据
```

### JsHttpResponse

HTTP响应对象：

```javascript
response.body()           // 获取响应体（字符串）
response.json()          // 解析JSON响应
response.statusCode()    // 获取HTTP状态码
response.header(name)    // 获取响应头
response.headers()       // 获取所有响应头
```

### JsLogger

日志记录器：

```javascript
logger.debug(message)    // 调试日志
logger.info(message)     // 信息日志
logger.warn(message)    // 警告日志
logger.error(message)    // 错误日志
```

### FileInfo

文件信息对象：

```javascript
{
    fileName: "文件名",
    fileId: "文件ID",
    fileType: "file|folder",
    size: 1024,
    sizeStr: "1KB",
    createTime: "2024-01-01",
    updateTime: "2024-01-01",
    createBy: "创建者",
    downloadCount: 100,
    fileIcon: "file",
    panType: "网盘类型",
    parserUrl: "解析URL",
    previewUrl: "预览URL"
}
```

## 开发提示

### VSCode支持

1. 确保安装了JavaScript扩展
2. `types.js` 文件提供类型定义和代码补全
3. `jsconfig.json` 配置了项目设置

### 调试

- 使用 `logger.debug()` 输出调试信息
- 查看应用日志了解解析过程
- 使用 `console.log()` 在Nashorn中输出信息

### 错误处理

```javascript
try {
    var response = http.get(url);
    if (response.statusCode() !== 200) {
        throw new Error("请求失败: " + response.statusCode());
    }
    return response.json();
} catch (e) {
    logger.error("解析失败: " + e.message);
    throw e;
}
```

## 示例

参考 `example-demo.js` 文件，它展示了完整的解析器实现，包括：

- 元数据配置
- 三个核心方法的实现
- 错误处理
- 日志记录
- 文件信息构建

## 注意事项

1. **ES5.1兼容**：只使用ES5.1语法，避免ES6+特性
2. **同步API**：HTTP客户端提供同步接口，无需处理异步回调
3. **全局函数**：解析器函数必须定义为全局函数，不能使用模块导出
4. **错误处理**：始终包含适当的错误处理和日志记录
5. **性能考虑**：避免在解析器中执行耗时操作

## 故障排除

### 常见问题

1. **解析器未加载**：检查元数据格式是否正确
2. **类型错误**：确保函数签名与接口匹配
3. **HTTP请求失败**：检查URL和网络连接
4. **JSON解析错误**：验证响应格式

### 日志查看

查看应用日志了解详细的执行过程和错误信息。
