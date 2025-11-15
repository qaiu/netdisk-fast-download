// ==UserScript==
// @name         咪咕音乐解析器
// @type         migu
// @displayName  咪咕音乐
// @description  解析咪咕音乐分享链接，获取歌曲下载地址
// @match        https?://c\.migu\.cn/(?<KEY>\w+)(\?.*)?
// @author       qaiu
// @version      2.0.0
// ==/UserScript==

/**
 * 从URL中提取参数值
 * @param {string} url - URL字符串
 * @param {string} paramName - 参数名
 * @returns {string|null} 参数值
 */
function getUrlParam(url, paramName) {
    var match = url.match(new RegExp("[?&]" + paramName + "=([^&]*)"));
    return match ? match[1] : null;
}

/**
 * 获取302重定向地址
 * @param {string} url - 原始URL
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 重定向后的URL
 */
function getRedirectUrl(url, http, logger) {
    try {
        logger.debug("获取重定向地址: " + url);
        
        // 清理URL，移除?后面的参数
        var cleanUrl = url;
        var questionMarkIndex = url.indexOf("?");
        if (questionMarkIndex !== -1) {
            cleanUrl = url.substring(0, questionMarkIndex);
        }
        logger.debug("清理后的URL: " + cleanUrl);
        
        // 使用getNoRedirect获取Location头
        var response = http.getNoRedirect(cleanUrl);
        var statusCode = response.statusCode();
        
        // 检查是否是重定向状态码
        if (statusCode >= 300 && statusCode < 400) {
            var location = response.header("Location");
            if (location) {
                // 处理相对路径
                if (location.indexOf("http") !== 0) {
                    var baseUrl = cleanUrl.substring(0, cleanUrl.indexOf("/", 8));
                    if (location.indexOf("/") === 0) {
                        location = baseUrl + location;
                    } else {
                        location = baseUrl + "/" + location;
                    }
                }
                logger.info("重定向到: " + location);
                return location;
            }
        }
        
        // 如果没有重定向，返回原URL
        logger.warn("未获取到重定向地址，状态码: " + statusCode);
        return cleanUrl;
        
    } catch (e) {
        logger.error("获取重定向地址失败: " + e.message);
        throw e;
    }
}

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    logger.info("===== 开始解析咪咕音乐 =====");
    
    try {
        var shareUrl = shareLinkInfo.getShareUrl();
        logger.info("分享URL: " + shareUrl);
        
        if (!shareUrl || shareUrl.indexOf("c.migu.cn") === -1) {
            throw new Error("无效的咪咕音乐分享链接");
        }
        
        // 设置请求头
        http.putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        http.putHeader("Referer", "https://music.migu.cn/");
        http.putHeader("Accept", "application/json, text/plain, */*");
        
        // 步骤1: 获取302重定向地址
        logger.info("步骤1: 获取302重定向地址...");
        var redirectUrl = getRedirectUrl(shareUrl, http, logger);
        logger.info("重定向地址: " + redirectUrl);
        
        // 步骤2: 从重定向地址中提取contentId (id参数)
        var contentId = getUrlParam(redirectUrl, "id");
        if (!contentId) {
            throw new Error("无法从重定向地址中提取contentId (id参数)");
        }
        logger.info("提取到contentId: " + contentId);
        
        // 步骤3: 调用API获取文件信息
        logger.info("步骤2: 获取文件信息...");
        var fileInfoUrl = "https://c.musicapp.migu.cn/MIGUM3.0/resource/song/by-contentids/v2.0?contentId=" + contentId;
        logger.debug("请求URL: " + fileInfoUrl);
        
        var fileInfoResponse = http.get(fileInfoUrl);
        if (fileInfoResponse.statusCode() !== 200) {
            throw new Error("获取文件信息失败，状态码: " + fileInfoResponse.statusCode());
        }
        
        var fileInfoData = fileInfoResponse.json();
        logger.debug("文件信息响应: " + JSON.stringify(fileInfoData));
        
        // 提取ringCopyrightId
        var ringCopyrightId = null;
        if (fileInfoData.data && fileInfoData.data.length > 0) {
            var songInfo = fileInfoData.data[0];
            ringCopyrightId = songInfo.ringCopyrightId;
            logger.info("歌曲名称: " + (songInfo.songName || "未知"));
            logger.info("提取到ringCopyrightId: " + ringCopyrightId);
        }
        
        if (!ringCopyrightId) {
            throw new Error("响应中未找到ringCopyrightId");
        }
        
        // 步骤4: 调用下载接口获取下载链接
        logger.info("步骤3: 获取下载链接...");
        
        // 设置完整的请求头（Referer使用302重定向地址）
        http.putHeader("Accept", "application/json, text/plain, */*");
        http.putHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,zh-TW;q=0.7");
        http.putHeader("Referer", redirectUrl);
        http.putHeader("Sec-Fetch-Dest", "empty");
        http.putHeader("Sec-Fetch-Mode", "cors");
        http.putHeader("Sec-Fetch-Site", "same-site");
        http.putHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36");
        http.putHeader("channel", "014021I");
        http.putHeader("subchannel", "014021I");
        
        var downloadApiUrl = "https://c.musicapp.migu.cn/MIGUM3.0/strategy/listen-url/v2.4" +
                            "?contentId=" + contentId +
                            "&copyrightId=" + ringCopyrightId +
                            "&resourceType=2" +
                            "&netType=01" +
                            "&toneFlag=PQ" +
                            "&scene=" +
                            "&lowerQualityContentId=" + contentId;
        
        logger.debug("请求URL: " + downloadApiUrl);
        logger.debug("Referer: " + redirectUrl);
        
        var downloadResponse = http.get(downloadApiUrl);
        if (downloadResponse.statusCode() !== 200) {
            throw new Error("获取下载链接失败，状态码: " + downloadResponse.statusCode());
        }
        
        var downloadData = downloadResponse.json();
        logger.info("下载链接响应: " + JSON.stringify(downloadData));
        
        // 提取最终下载链接
        if (downloadData.data && downloadData.data.url) {
            var downloadUrl = downloadData.data.url;
            logger.info("解析成功，下载链接: " + downloadUrl);
            return downloadUrl;
        } else {
            throw new Error("响应中未找到下载链接");
        }
        
    } catch (e) {
        logger.error("解析失败: " + e.message);
        throw e;
    }
}

/**
 * 解析文件列表（可选）
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {FileInfo[]} 文件信息列表
 */
function parseFileList(shareLinkInfo, http, logger) {
    // 咪咕音乐通常是单曲，不需要实现文件列表
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
    // 使用相同的解析逻辑
    return parse(shareLinkInfo, http, logger);
}
