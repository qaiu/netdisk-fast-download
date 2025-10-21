// ==UserScript==
// @name         一刻相册解析器
// @type         baidu_photo
// @displayName  百度一刻相册(JS)
// @description  解析百度一刻相册分享链接，获取文件列表和下载链接
// @match        https?://photo\.baidu\.com/photo/(web/share\?inviteCode=|wap/albumShare\?shareId=)(?<KEY>\w+)
// @author       qaiu
// @version      1.0.0
// ==/UserScript==

/**
 * API端点配置
 */
var API_CONFIG = {
    // 文件夹分享：通过pcode获取share_id
    QUERY_PCODE: "https://photo.baidu.com/youai/album/v1/querypcode",
    // 文件列表：获取文件列表
    LIST_FILES: "https://photo.baidu.com/youai/share/v2/list",
    
    // 请求参数
    CLIENT_TYPE: "70",
    LIMIT: "100"
};

/**
 * 设置标准请求头
 * @param {JsHttpClient} http - HTTP客户端
 * @param {string} referer - Referer URL
 */
function setStandardHeaders(http, referer) {
    var headers = {
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
        "Content-Type": "application/x-www-form-urlencoded",
        "DNT": "1",
        "Origin": "https://photo.baidu.com",
        "Pragma": "no-cache",
        "Referer": referer,
        "Sec-Fetch-Dest": "empty",
        "Sec-Fetch-Mode": "cors",
        "Sec-Fetch-Site": "same-origin",
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0",
        "X-Requested-With": "XMLHttpRequest",
        "sec-ch-ua": "\"Microsoft Edge\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"",
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": "\"macOS\""
    };
    
    for (var key in headers) {
        http.putHeader(key, headers[key]);
    }
}

/**
 * 获取分享ID
 * @param {string} shareKey - 分享键
 * @param {boolean} isFileShare - 是否为文件分享
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 分享ID
 */
function getShareId(shareKey, isFileShare, http, logger) {
    if (isFileShare) {
        logger.info("文件分享模式，直接使用shareId: " + shareKey);
        return shareKey;
    }
    
    // 文件夹分享：通过pcode获取share_id
    var queryUrl = API_CONFIG.QUERY_PCODE + "?clienttype=" + API_CONFIG.CLIENT_TYPE + "&pcode=" + shareKey + "&web=1";
    logger.debug("文件夹分享查询URL: " + queryUrl);
    
    setStandardHeaders(http, "https://photo.baidu.com/photo/web/share?inviteCode=" + shareKey);
    
    var queryResponse = http.get(queryUrl);
    if (queryResponse.statusCode() !== 200) {
        throw new Error("获取分享ID失败，状态码: " + queryResponse.statusCode());
    }
    
    var queryData = queryResponse.json();
    logger.debug("查询响应: " + JSON.stringify(queryData));
    
    if (queryData.errno !== undefined && queryData.errno !== 0) {
        throw new Error("API返回错误，errno: " + queryData.errno);
    }
    
    var shareId = queryData.pdata && queryData.pdata.share_id;
    if (!shareId) {
        throw new Error("未找到share_id");
    }
    
    logger.info("获取到分享ID: " + shareId);
    return shareId;
}

/**
 * 获取文件列表
 * @param {string} shareId - 分享ID
 * @param {string} shareKey - 分享键
 * @param {boolean} isFileShare - 是否为文件分享
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {Array} 文件列表
 */
function getFileList(shareId, shareKey, isFileShare, http, logger) {
    var listUrl = API_CONFIG.LIST_FILES + "?clienttype=" + API_CONFIG.CLIENT_TYPE + "&share_id=" + shareId + "&limit=" + API_CONFIG.LIMIT;
    logger.debug("获取文件列表 URL: " + listUrl);
    
    var referer = isFileShare ? 
        "https://photo.baidu.com/photo/wap/albumShare?shareId=" + shareKey : 
        "https://photo.baidu.com/photo/web/share?inviteCode=" + shareKey;
    
    setStandardHeaders(http, referer);
    
    var listResponse = http.get(listUrl);
    if (listResponse.statusCode() !== 200) {
        throw new Error("获取文件列表失败，状态码: " + listResponse.statusCode());
    }
    
    var listData = listResponse.json();
    logger.debug("文件列表响应: " + JSON.stringify(listData));
    
    if (listData.errno !== undefined && listData.errno !== 0) {
        throw new Error("获取文件列表API返回错误，errno: " + listData.errno);
    }
    
    var fileList = listData.list;
    if (!fileList || fileList.length === 0) {
        logger.warn("文件列表为空");
        return [];
    }
    
    logger.info("获取到文件列表，共 " + fileList.length + " 个文件");
    return fileList;
}

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
 * @param {JsHttpClient} http - HTTP客户端实例
 * @param {JsLogger} logger - 日志记录器实例
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    logger.info("===== 开始执行 parse 方法 =====");
    
    var shareKey = shareLinkInfo.getShareKey();
    logger.info("分享Key: " + shareKey);
    
    try {
        // 判断分享类型
        // 如果shareKey是纯数字且长度较长，很可能是文件分享的share_id
        // 如果shareKey包含字母，很可能是文件夹分享的inviteCode
        var isFileShare = /^\d{10,}$/.test(shareKey);
        logger.info("分享类型: " + (isFileShare ? "文件分享" : "文件夹分享"));
        
        // 获取分享ID
        var shareId = getShareId(shareKey, isFileShare, http, logger);
        
        // 获取文件列表
        var fileList = getFileList(shareId, shareKey, isFileShare, http, logger);
        
        if (fileList.length === 0) {
            throw new Error("文件列表为空");
        }
        
        // 返回第一个文件的下载链接
        var firstFile = fileList[0];
        var downloadUrl = firstFile.dlink;
        
        if (!downloadUrl) {
            throw new Error("未找到下载链接");
        }
        
        // 获取真实的下载链接（处理302重定向）
        var realDownloadUrl = getRealDownloadUrl(downloadUrl, http, logger);
        
        logger.info("解析成功，返回URL: " + realDownloadUrl);
        return realDownloadUrl;
        
    } catch (e) {
        logger.error("解析失败: " + e.message);
        throw new Error("解析失败: " + e.message);
    }
}

/**
 * 解析文件列表
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
 * @param {JsHttpClient} http - HTTP客户端实例
 * @param {JsLogger} logger - 日志记录器实例
 * @returns {FileInfo[]} 文件信息列表
 */
function parseFileList(shareLinkInfo, http, logger) {
    logger.info("===== 开始执行 parseFileList 方法 =====");
    
    var shareKey = shareLinkInfo.getShareKey();
    logger.info("分享Key: " + shareKey);
    
    try {
        // 判断分享类型
        // 如果shareKey是纯数字且长度较长，很可能是文件分享的share_id
        // 如果shareKey包含字母，很可能是文件夹分享的inviteCode
        var isFileShare = /^\d{10,}$/.test(shareKey);
        logger.info("分享类型: " + (isFileShare ? "文件分享" : "文件夹分享"));
        
        // 获取分享ID
        var shareId = getShareId(shareKey, isFileShare, http, logger);
        
        // 获取文件列表
        var fileList = getFileList(shareId, shareKey, isFileShare, http, logger);
        
        if (fileList.length === 0) {
            logger.warn("文件列表为空");
            return [];
        }
        
        logger.info("解析文件列表成功，共 " + fileList.length + " 项");
        
        var result = [];
        for (var i = 0; i < fileList.length; i++) {
            var file = fileList[i];
            
            /** @type {FileInfo} */
            var fileInfo = {
                fileName: extractFileName(file.path) || ("文件_" + (i + 1)),
                fileId: String(file.fsid),
                fileType: "file",
                size: file.size || 0,
                sizeStr: formatBytes(file.size || 0),
                createTime: formatTimestamp(file.ctime),
                updateTime: formatTimestamp(file.mtime),
                createBy: "",
                downloadCount: 0,
                fileIcon: "file",
                panType: "baidu_photo",
                parserUrl: "",
                previewUrl: ""
            };
            
            // 设置下载链接
            if (file.dlink) {
                fileInfo.parserUrl = file.dlink;
            }
            
            // 设置预览链接（取第一个缩略图）
            if (file.thumburl && file.thumburl.length > 0) {
                fileInfo.previewUrl = file.thumburl[0];
            }
            
            result.push(fileInfo);
        }
        
        logger.info("文件列表解析成功，共 " + result.length + " 个文件");
        return result;
        
    } catch (e) {
        logger.error("解析文件列表失败: " + e.message);
        throw new Error("解析文件列表失败: " + e.message);
    }
}

/**
 * 根据文件ID获取下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息对象
 * @param {JsHttpClient} http - HTTP客户端实例
 * @param {JsLogger} logger - 日志记录器实例
 * @returns {string} 下载链接
 */
function parseById(shareLinkInfo, http, logger) {
    logger.info("===== 开始执行 parseById 方法 =====");
    
    var shareKey = shareLinkInfo.getShareKey();
    var otherParam = shareLinkInfo.getOtherParam("paramJson");
    var fileId = otherParam ? otherParam.fileId || otherParam.id : null;
    
    logger.info("分享Key: " + shareKey);
    logger.info("文件ID: " + fileId);
    
    if (!fileId) {
        throw new Error("未提供文件ID");
    }
    
    try {
        // 判断分享类型
        // 如果shareKey是纯数字且长度较长，很可能是文件分享的share_id
        // 如果shareKey包含字母，很可能是文件夹分享的inviteCode
        var isFileShare = /^\d{10,}$/.test(shareKey);
        logger.info("分享类型: " + (isFileShare ? "文件分享" : "文件夹分享"));
        
        // 获取分享ID
        var shareId = getShareId(shareKey, isFileShare, http, logger);
        
        // 获取文件列表
        var fileList = getFileList(shareId, shareKey, isFileShare, http, logger);
        
        if (fileList.length === 0) {
            throw new Error("文件列表为空");
        }
        
        // 查找指定ID的文件
        var targetFile = null;
        for (var i = 0; i < fileList.length; i++) {
            var file = fileList[i];
            if (String(file.fsid) == fileId || String(i) == fileId) {
                targetFile = file;
                break;
            }
        }
        
        if (!targetFile) {
            throw new Error("未找到指定ID的文件: " + fileId);
        }
        
        var downloadUrl = targetFile.dlink;
        if (!downloadUrl) {
            throw new Error("文件无下载链接");
        }
        
        // 获取真实的下载链接（处理302重定向）
        var realDownloadUrl = getRealDownloadUrl(downloadUrl, http, logger);
        
        logger.info("根据ID解析成功: " + realDownloadUrl);
        return realDownloadUrl;
        
    } catch (e) {
        logger.error("根据ID解析失败: " + e.message);
        throw new Error("根据ID解析失败: " + e.message);
    }
}

/**
 * 格式化字节大小
 * @param {number} bytes
 * @returns {string}
 */
function formatBytes(bytes) {
    if (bytes === 0) return "0 B";
    var k = 1024;
    var sizes = ["B", "KB", "MB", "GB", "TB"];
    var i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(2) + " " + sizes[i];
}

/**
 * 从路径中提取文件名
 * @param {string} path
 * @returns {string}
 */
function extractFileName(path) {
    if (!path) return "";
    var parts = path.split("/");
    return parts[parts.length - 1] || "";
}

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

/**
 * 格式化时间戳
 * @param {number} timestamp
 * @returns {string}
 */
function formatTimestamp(timestamp) {
    if (!timestamp) return "";
    var date = new Date(timestamp * 1000);
    return date.toISOString().replace("T", " ").substring(0, 19);
}
