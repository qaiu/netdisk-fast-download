// ==UserScript==
// @name         演示解析器
// @type         demo_js
// @displayName  演示网盘(JS)
// @description  演示JavaScript解析器的完整功能（使用JSONPlaceholder测试API）
// @match        https?://demo\.example\.com/s/(?<KEY>\w+)
// @author       qaiu
// @version      1.0.0
// ==/UserScript==

// 注意：require调用仅用于IDE类型提示，运行时会被忽略
// var types = require('./types');

/**
 * 解析单个文件下载链接
 * 使用 https://jsonplaceholder.typicode.com/posts/1 作为测试
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接URL
 */
function parse(shareLinkInfo, http, logger) {
    logger.info("===== 开始执行 parse 方法 =====");
    
    var shareKey = shareLinkInfo.getShareKey();
    var password = shareLinkInfo.getSharePassword();
    
    logger.info("分享Key: " + shareKey);
    logger.info("分享密码: " + (password || "无"));
    
    // 使用JSONPlaceholder测试API
    var apiUrl = "https://jsonplaceholder.typicode.com/posts/" + (shareKey || "1");
    logger.debug("请求URL: " + apiUrl);
    
    try {
        var response = http.get(apiUrl);
        logger.debug("HTTP状态码: " + response.statusCode());
        
        var data = response.json();
        logger.debug("响应数据: " + JSON.stringify(data));
        
        // 模拟返回下载链接（实际是返回post的标题作为"下载链接"）
        var downloadUrl = "https://cdn.example.com/file/" + data.id + "/" + data.title;
        logger.info("解析成功，返回URL: " + downloadUrl);
        
        return downloadUrl;
    } catch (e) {
        logger.error("解析失败: " + e.message);
        throw new Error("解析失败: " + e.message);
    }
}

/**
 * 解析文件列表
 * 使用 https://jsonplaceholder.typicode.com/users 作为测试
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {FileInfo[]} 文件列表数组
 */
function parseFileList(shareLinkInfo, http, logger) {
    logger.info("===== 开始执行 parseFileList 方法 =====");
    
    var dirId = shareLinkInfo.getOtherParam("dirId") || "1";
    logger.info("目录ID: " + dirId);
    
    // 使用JSONPlaceholder的users API模拟文件列表
    var apiUrl = "https://jsonplaceholder.typicode.com/users";
    logger.debug("请求URL: " + apiUrl);
    
    try {
        var response = http.get(apiUrl);
        var users = response.json();
        
        var fileList = [];
        for (var i = 0; i < users.length; i++) {
            var user = users[i];
            
            // 模拟文件和目录
            var isFolder = (user.id % 3 === 0); // 每3个作为目录
            var fileSize = isFolder ? 0 : user.id * 1024 * 1024; // 模拟文件大小
            
            /** @type {FileInfo} */
            var fileInfo = {
                fileName: user.name + (isFolder ? " [目录]" : ".txt"),
                fileId: user.id.toString(),
                fileType: isFolder ? "folder" : "file",
                size: fileSize,
                sizeStr: formatFileSize(fileSize),
                createTime: "2024-01-01",
                updateTime: "2024-01-01",
                createBy: user.username,
                downloadCount: Math.floor(Math.random() * 1000),
                fileIcon: isFolder ? "folder" : "file",
                panType: "demo_js",
                parserUrl: "",
                previewUrl: ""
            };
            
            // 如果是目录，设置解析URL
            if (isFolder) {
                fileInfo.parserUrl = "/v2/getFileList?url=demo&dirId=" + user.id;
            } else {
                // 如果是文件，设置下载URL
                fileInfo.parserUrl = "/v2/redirectUrl/demo_js/" + user.id;
            }
            
            fileList.push(fileInfo);
        }
        
        logger.info("解析文件列表成功，共 " + fileList.length + " 项");
        return fileList;
        
    } catch (e) {
        logger.error("解析文件列表失败: " + e.message);
        throw new Error("解析文件列表失败: " + e.message);
    }
}

/**
 * 根据文件ID获取下载链接
 * 使用 https://jsonplaceholder.typicode.com/todos/:id 作为测试
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接URL
 */
function parseById(shareLinkInfo, http, logger) {
    logger.info("===== 开始执行 parseById 方法 =====");
    
    var paramJson = shareLinkInfo.getOtherParam("paramJson");
    if (!paramJson) {
        throw new Error("缺少paramJson参数");
    }
    
    var fileId = paramJson.fileId || paramJson.id || "1";
    logger.info("文件ID: " + fileId);
    
    // 使用JSONPlaceholder的todos API
    var apiUrl = "https://jsonplaceholder.typicode.com/todos/" + fileId;
    logger.debug("请求URL: " + apiUrl);
    
    try {
        var response = http.get(apiUrl);
        var todo = response.json();
        
        // 模拟返回下载链接
        var downloadUrl = "https://cdn.example.com/download/" + todo.id + "/" + todo.title + ".zip";
        logger.info("根据ID解析成功: " + downloadUrl);
        
        return downloadUrl;
        
    } catch (e) {
        logger.error("根据ID解析失败: " + e.message);
        throw new Error("根据ID解析失败: " + e.message);
    }
}

/**
 * 辅助函数：格式化文件大小
 * @param {number} bytes - 字节数
 * @returns {string} 格式化后的大小
 */
function formatFileSize(bytes) {
    if (bytes === 0) return "0B";
    var k = 1024;
    var sizes = ["B", "KB", "MB", "GB", "TB"];
    var i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + sizes[i];
}
