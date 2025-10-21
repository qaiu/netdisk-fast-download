/**
 * JavaScript解析器类型定义文件
 * 使用JSDoc注释提供代码补全和类型提示
 * 兼容ES5.1和Nashorn引擎
 */

// 全局类型定义，使用JSDoc注释
// 这些类型定义将在VSCode中提供代码补全和类型检查

/**
 * @typedef {Object} ShareLinkInfo
 * @property {function(): string} getShareUrl - 获取分享URL
 * @property {function(): string} getShareKey - 获取分享Key
 * @property {function(): string} getSharePassword - 获取分享密码
 * @property {function(): string} getType - 获取网盘类型
 * @property {function(): string} getPanName - 获取网盘名称
 * @property {function(string): any} getOtherParam - 获取其他参数
 */

/**
 * @typedef {Object} JsHttpResponse
 * @property {function(): string} body - 获取响应体（字符串）
 * @property {function(): any} json - 解析JSON响应
 * @property {function(): number} statusCode - 获取HTTP状态码
 * @property {function(string): string|null} header - 获取响应头
 * @property {function(): Object} headers - 获取所有响应头
 */

/**
 * @typedef {Object} JsHttpClient
 * @property {function(string): JsHttpResponse} get - 发起GET请求
 * @property {function(string, any=): JsHttpResponse} post - 发起POST请求
 * @property {function(string, string): JsHttpClient} putHeader - 设置请求头
 * @property {function(Object): JsHttpResponse} sendForm - 发送表单数据
 * @property {function(any): JsHttpResponse} sendJson - 发送JSON数据
 */

/**
 * @typedef {Object} JsLogger
 * @property {function(string): void} debug - 调试日志
 * @property {function(string): void} info - 信息日志
 * @property {function(string): void} warn - 警告日志
 * @property {function(string): void} error - 错误日志
 */

/**
 * @typedef {Object} FileInfo
 * @property {string} fileName - 文件名
 * @property {string} fileId - 文件ID
 * @property {string} fileType - 文件类型: "file" | "folder"
 * @property {number} size - 文件大小(字节)
 * @property {string} sizeStr - 文件大小(可读格式)
 * @property {string} createTime - 创建时间
 * @property {string} updateTime - 更新时间
 * @property {string} createBy - 创建者
 * @property {number} downloadCount - 下载次数
 * @property {string} fileIcon - 文件图标
 * @property {string} panType - 网盘类型
 * @property {string} parserUrl - 解析URL
 * @property {string} previewUrl - 预览URL
 */

/**
 * @typedef {Object} ParserExports
 * @property {function(ShareLinkInfo, JsHttpClient, JsLogger): string} parse - 解析单个文件下载链接
 * @property {function(ShareLinkInfo, JsHttpClient, JsLogger): FileInfo[]} parseFileList - 解析文件列表
 * @property {function(ShareLinkInfo, JsHttpClient, JsLogger): string} parseById - 根据文件ID获取下载链接
 */
