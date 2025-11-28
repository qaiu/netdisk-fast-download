/**
 * JavaScript解析器类型定义文件
 * 使用JSDoc注释提供代码补全和类型提示
 * 兼容ES5.1和Nashorn引擎
 */

// 全局类型定义，使用JSDoc注释
// 这些类型定义将在VSCode中提供代码补全和类型检查

// ============================================================================
// Nashorn Java 互操作全局对象
// ============================================================================

/**
 * Java 全局对象类型定义 (Nashorn引擎提供)
 * 用于访问Java类型和进行Java互操作
 * @typedef {Object} JavaGlobal
 * @property {function(string): any} type - 获取Java类，参数为完整类名（如"java.util.zip.CRC32"）
 * @property {function(any): any} from - 将Java对象转换为JavaScript对象
 * @property {function(any): any} to - 将JavaScript对象转换为Java对象
 * @property {function(any): boolean} isType - 检查对象是否为指定Java类型
 * @property {function(any): boolean} isJavaObject - 检查对象是否为Java对象
 * @property {function(any): boolean} isJavaMethod - 检查对象是否为Java方法
 * @property {function(any): boolean} isJavaFunction - 检查对象是否为Java函数
 */

/**
 * Java 全局对象 (Nashorn引擎提供)
 * @global
 * @type {JavaGlobal}
 */
var Java;

/**
 * java 命名空间对象类型定义 (Nashorn引擎提供)
 * 用于直接访问Java包和类
 * @typedef {Object} JavaNamespace
 * @property {Object} lang - java.lang 包
 * @property {Object} util - java.util 包
 * @property {Object} io - java.io 包
 * @property {Object} net - java.net 包
 * @property {Object} math - java.math 包
 * @property {Object} security - java.security 包
 * @property {Object} text - java.text 包
 * @property {Object} time - java.time 包
 */

/**
 * java 命名空间对象 (Nashorn引擎提供)
 * @global
 * @type {JavaNamespace}
 */
var java;

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
 * @property {function(): boolean} isSuccess - 检查请求是否成功（2xx状态码）
 * @property {function(): Array} bodyBytes - 获取响应体字节数组
 * @property {function(): number} bodySize - 获取响应体大小（字节）
 */

/**
 * @typedef {Object} JsHttpClient
 * @property {function(string): JsHttpResponse} get - 发起GET请求
 * @property {function(string): JsHttpResponse} getWithRedirect - 发起GET请求并跟随重定向
 * @property {function(string): JsHttpResponse} getNoRedirect - 发起GET请求但不跟随重定向（用于获取Location头）
 * @property {function(string, any=): JsHttpResponse} post - 发起POST请求
 * @property {function(string, any=): JsHttpResponse} put - 发起PUT请求
 * @property {function(string): JsHttpResponse} delete - 发起DELETE请求
 * @property {function(string, any=): JsHttpResponse} patch - 发起PATCH请求
 * @property {function(string, string): JsHttpClient} putHeader - 设置请求头
 * @property {function(Object): JsHttpClient} putHeaders - 批量设置请求头
 * @property {function(string): JsHttpClient} removeHeader - 删除指定请求头
 * @property {function(): JsHttpClient} clearHeaders - 清空所有请求头（保留默认头）
 * @property {function(): Object} getHeaders - 获取所有请求头
 * @property {function(number): JsHttpClient} setTimeout - 设置请求超时时间（秒）
 * @property {function(Object): JsHttpResponse} sendForm - 发送简单表单数据
 * @property {function(string, Object): JsHttpResponse} sendMultipartForm - 发送multipart表单数据（仅支持文本字段）
 * @property {function(any): JsHttpResponse} sendJson - 发送JSON数据
 * @property {function(string): string} urlEncode - URL编码（静态方法）
 * @property {function(string): string} urlDecode - URL解码（静态方法）
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

// ============================================================================
// Java 基础类型定义
// ============================================================================

/**
 * Java byte 类型 (8位有符号整数)
 * 范围: -128 到 127
 * @typedef {number} JavaByte
 */

/**
 * Java short 类型 (16位有符号整数)
 * 范围: -32,768 到 32,767
 * @typedef {number} JavaShort
 */

/**
 * Java int 类型 (32位有符号整数)
 * 范围: -2,147,483,648 到 2,147,483,647
 * @typedef {number} JavaInt
 */

/**
 * Java long 类型 (64位有符号整数)
 * 范围: -9,223,372,036,854,775,808 到 9,223,372,036,854,775,807
 * @typedef {number} JavaLong
 */

/**
 * Java float 类型 (32位单精度浮点数)
 * @typedef {number} JavaFloat
 */

/**
 * Java double 类型 (64位双精度浮点数)
 * @typedef {number} JavaDouble
 */

/**
 * Java char 类型 (16位Unicode字符)
 * @typedef {string|number} JavaChar
 */

/**
 * Java boolean 类型 (布尔值)
 * @typedef {boolean} JavaBoolean
 */

/**
 * Java String 类型 (字符串)
 * @typedef {string} JavaString
 */

/**
 * Java Byte 包装类型
 * @typedef {Object} JavaByteWrapper
 * @property {function(): number} byteValue - 返回byte值
 * @property {function(): number} intValue - 返回int值
 * @property {function(): number} longValue - 返回long值
 * @property {function(): number} floatValue - 返回float值
 * @property {function(): number} doubleValue - 返回double值
 * @property {function(JavaByteWrapper): number} compareTo - 比较两个Byte对象
 * @property {function(): string} toString - 转换为字符串
 */

/**
 * Java Short 包装类型
 * @typedef {Object} JavaShortWrapper
 * @property {function(): number} shortValue - 返回short值
 * @property {function(): number} intValue - 返回int值
 * @property {function(): number} longValue - 返回long值
 * @property {function(): number} floatValue - 返回float值
 * @property {function(): number} doubleValue - 返回double值
 * @property {function(JavaShortWrapper): number} compareTo - 比较两个Short对象
 * @property {function(): string} toString - 转换为字符串
 */

/**
 * Java Integer 包装类型
 * @typedef {Object} JavaIntegerWrapper
 * @property {function(): number} intValue - 返回int值
 * @property {function(): number} longValue - 返回long值
 * @property {function(): number} floatValue - 返回float值
 * @property {function(): number} doubleValue - 返回double值
 * @property {function(JavaIntegerWrapper): number} compareTo - 比较两个Integer对象
 * @property {function(): string} toString - 转换为字符串
 * @property {function(number): JavaIntegerWrapper} valueOf - 静态方法：创建Integer对象
 * @property {function(string): number} parseInt - 静态方法：解析字符串为int
 */

/**
 * Java Long 包装类型
 * @typedef {Object} JavaLongWrapper
 * @property {function(): number} longValue - 返回long值
 * @property {function(): number} intValue - 返回int值
 * @property {function(): number} floatValue - 返回float值
 * @property {function(): number} doubleValue - 返回double值
 * @property {function(JavaLongWrapper): number} compareTo - 比较两个Long对象
 * @property {function(): string} toString - 转换为字符串
 * @property {function(number): JavaLongWrapper} valueOf - 静态方法：创建Long对象
 * @property {function(string): number} parseLong - 静态方法：解析字符串为long
 */

/**
 * Java Float 包装类型
 * @typedef {Object} JavaFloatWrapper
 * @property {function(): number} floatValue - 返回float值
 * @property {function(): number} doubleValue - 返回double值
 * @property {function(): number} intValue - 返回int值
 * @property {function(): number} longValue - 返回long值
 * @property {function(JavaFloatWrapper): number} compareTo - 比较两个Float对象
 * @property {function(): string} toString - 转换为字符串
 * @property {function(number): JavaFloatWrapper} valueOf - 静态方法：创建Float对象
 * @property {function(string): number} parseFloat - 静态方法：解析字符串为float
 */

/**
 * Java Double 包装类型
 * @typedef {Object} JavaDoubleWrapper
 * @property {function(): number} doubleValue - 返回double值
 * @property {function(): number} floatValue - 返回float值
 * @property {function(): number} intValue - 返回int值
 * @property {function(): number} longValue - 返回long值
 * @property {function(JavaDoubleWrapper): number} compareTo - 比较两个Double对象
 * @property {function(): string} toString - 转换为字符串
 * @property {function(number): JavaDoubleWrapper} valueOf - 静态方法：创建Double对象
 * @property {function(string): number} parseDouble - 静态方法：解析字符串为double
 */

/**
 * Java Character 包装类型
 * @typedef {Object} JavaCharacterWrapper
 * @property {function(): string|number} charValue - 返回char值
 * @property {function(JavaCharacterWrapper): number} compareTo - 比较两个Character对象
 * @property {function(): string} toString - 转换为字符串
 * @property {function(string|number): boolean} isDigit - 静态方法：判断是否为数字
 * @property {function(string|number): boolean} isLetter - 静态方法：判断是否为字母
 * @property {function(string|number): boolean} isLetterOrDigit - 静态方法：判断是否为字母或数字
 * @property {function(string|number): boolean} isUpperCase - 静态方法：判断是否为大写
 * @property {function(string|number): boolean} isLowerCase - 静态方法：判断是否为小写
 * @property {function(string|number): string|number} toUpperCase - 静态方法：转换为大写
 * @property {function(string|number): string|number} toLowerCase - 静态方法：转换为小写
 */

/**
 * Java Boolean 包装类型
 * @typedef {Object} JavaBooleanWrapper
 * @property {function(): boolean} booleanValue - 返回boolean值
 * @property {function(JavaBooleanWrapper): number} compareTo - 比较两个Boolean对象
 * @property {function(): string} toString - 转换为字符串
 * @property {function(boolean): JavaBooleanWrapper} valueOf - 静态方法：创建Boolean对象
 * @property {function(string): boolean} parseBoolean - 静态方法：解析字符串为boolean
 */
