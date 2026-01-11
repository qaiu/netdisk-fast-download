/**
 * JavaScript 解析器模板
 * 包含解析器的基础模板代码
 */

/**
 * 生成 JavaScript 解析器模板代码
 * @param {string} name - 解析器名称
 * @param {string} identifier - 标识符
 * @param {string} author - 作者
 * @param {string} match - URL匹配模式
 * @returns {string} JavaScript模板代码
 */
export const generateJsTemplate = (name, identifier, author, match) => {
  const type = identifier.toLowerCase().replace(/[^a-z0-9]/g, '_');
  const displayName = name;
  const description = `使用JavaScript实现的${name}解析器`;
  
  return `// ==UserScript==
// @name         ${name}
// @type         ${type}
// @displayName  ${displayName}
// @description  ${description}
// @match        ${match || 'https?://example.com/s/(?<KEY>\\\\w+)'}
// @author       ${author || 'yourname'}
// @version      1.0.0
// ==/UserScript==

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    var url = shareLinkInfo.getShareUrl();
    logger.info("开始解析: " + url);
    
    var response = http.get(url);
    if (!response.isSuccess()) {
        throw new Error("请求失败: " + response.statusCode());
    }
    
    var html = response.body();
    // 这里添加你的解析逻辑
    // 例如：使用正则表达式提取下载链接
    
    return "https://example.com/download/file.zip";
}

/**
 * 解析文件列表（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {Array} 文件信息数组
 */
function parseFileList(shareLinkInfo, http, logger) {
    var dirId = shareLinkInfo.getOtherParam("dirId") || "0";
    logger.info("解析文件列表，目录ID: " + dirId);
    
    // 这里添加你的文件列表解析逻辑
    var fileList = [];
    
    return fileList;
}`;
};

/**
 * JavaScript 解析器的默认空白模板
 */
export const JS_EMPTY_TEMPLATE = `// ==UserScript==
// @name         新解析器
// @type         new_parser
// @displayName  新解析器
// @description  解析器描述
// @match        https?://example.com/s/(?<KEY>\\w+)
// @author       yourname
// @version      1.0.0
// ==/UserScript==

function parse(shareLinkInfo, http, logger) {
    var url = shareLinkInfo.getShareUrl();
    logger.info("开始解析: " + url);
    
    // 在这里编写你的解析逻辑
    
    return "";
}
`;

/**
 * JavaScript HTTP 请求示例模板
 */
export const JS_HTTP_EXAMPLE = `// HTTP 请求示例

// GET 请求
var response = http.get("https://api.example.com/data");
if (response.isSuccess()) {
    var json = JSON.parse(response.body());
    logger.info("获取数据成功");
}

// POST 请求（表单数据）
var formData = {
    "key": "value",
    "name": "test"
};
var postResponse = http.post("https://api.example.com/submit", formData);

// POST 请求（JSON数据）
var jsonData = JSON.stringify({ id: 1, name: "test" });
var headers = { "Content-Type": "application/json" };
var jsonResponse = http.postJson("https://api.example.com/api", jsonData, headers);

// 自定义请求头
var customHeaders = {
    "User-Agent": "Mozilla/5.0",
    "Referer": "https://example.com"
};
var customResponse = http.getWithHeaders("https://api.example.com/data", customHeaders);
`;

/**
 * JavaScript 正则表达式示例
 */
export const JS_REGEX_EXAMPLE = `// 正则表达式示例

var html = response.body();

// 匹配下载链接
var downloadMatch = html.match(/href=["']([^"']*\\.zip)["']/);
if (downloadMatch) {
    var downloadUrl = downloadMatch[1];
}

// 匹配JSON数据
var jsonMatch = html.match(/var data = (\\{[^}]+\\})/);
if (jsonMatch) {
    var data = JSON.parse(jsonMatch[1]);
}

// 全局匹配
var allLinks = html.match(/href=["']([^"']+)["']/g);
`;

export default {
  generateJsTemplate,
  JS_EMPTY_TEMPLATE,
  JS_HTTP_EXAMPLE,
  JS_REGEX_EXAMPLE
};
