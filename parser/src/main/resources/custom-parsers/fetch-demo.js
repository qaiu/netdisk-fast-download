// ==UserScript==
// @name         Fetch API示例解析器
// @type         fetch_demo
// @displayName  Fetch演示
// @description  演示如何在ES5环境中使用fetch API和async/await
// @match        https?://example\.com/s/(?<KEY>\w+)
// @author       QAIU
// @version      1.0.0
// ==/UserScript==

// 使用require导入类型定义（仅用于IDE类型提示）
var types = require('./types');
/** @typedef {types.ShareLinkInfo} ShareLinkInfo */
/** @typedef {types.JsHttpClient} JsHttpClient */
/** @typedef {types.JsLogger} JsLogger */

/**
 * 演示使用fetch API的解析器
 * 注意：虽然源码中使用了ES6+语法（async/await），但在浏览器中会被编译为ES5
 * 
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端（传统方式）
 * @param {JsLogger} logger - 日志对象
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    logger.info("=== Fetch API Demo ===");
    
    // 方式1：使用传统的http对象（同步）
    logger.info("方式1: 使用传统http对象");
    var response1 = http.get("https://httpbin.org/get");
    logger.info("状态码: " + response1.statusCode());
    
    // 方式2：使用fetch API（基于Promise）
    logger.info("方式2: 使用fetch API");
    
    // 注意：在ES5环境中，我们需要手动处理Promise
    // 这个示例展示了如何在ES5中使用fetch
    var fetchPromise = fetch("https://httpbin.org/get");
    
    // 等待Promise完成（同步等待模拟）
    var result = null;
    var error = null;
    
    fetchPromise
        .then(function(response) {
            logger.info("Fetch响应状态: " + response.status);
            return response.text();
        })
        .then(function(text) {
            logger.info("Fetch响应内容: " + text.substring(0, 100) + "...");
            result = "https://example.com/download/demo.file";
        })
        ['catch'](function(err) {
            logger.error("Fetch失败: " + err.message);
            error = err;
        });
    
    // 简单的等待循环（实际场景中不推荐，这里仅作演示）
    var timeout = 5000; // 5秒超时
    var start = Date.now();
    while (result === null && error === null && (Date.now() - start) < timeout) {
        // 等待Promise完成
        java.lang.Thread.sleep(10);
    }
    
    if (error !== null) {
        throw error;
    }
    
    if (result === null) {
        throw new Error("Fetch超时");
    }
    
    return result;
}

/**
 * 演示POST请求
 */
function demonstratePost(logger) {
    logger.info("=== 演示POST请求 ===");
    
    var postPromise = fetch("https://httpbin.org/post", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            key: "value",
            demo: true
        })
    });
    
    postPromise
        .then(function(response) {
            return response.json();
        })
        .then(function(data) {
            logger.info("POST响应: " + JSON.stringify(data));
        })
        ['catch'](function(err) {
            logger.error("POST失败: " + err.message);
        });
}
