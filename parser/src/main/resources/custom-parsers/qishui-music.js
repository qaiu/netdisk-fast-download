// ==UserScript==
// @name         汽水音乐解析器
// @type         qishui_music
// @displayName  汽水音乐
// @description  解析汽水音乐分享链接，获取音乐文件下载链接
// @match        https://music\.douyin\.com/qishui/share/track\?(.*&)?track_id=(?<KEY>\d+)
// @author       qaiu
// @version      2.0.1
// ==/UserScript==

/**
 * 跟踪302重定向，获取真实URL
 * @param {string} url - 原始URL
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 真实URL
 */
function getRealUrl(url, http, logger) {
    try {
        logger.debug("跟踪重定向: " + url);
        // 使用getNoRedirect获取Location头
        var response = http.getNoRedirect(url);
        var statusCode = response.statusCode();

        // 检查是否是重定向状态码 (301, 302, 303, 307, 308)
        if (statusCode >= 300 && statusCode < 400) {
            var location = response.header("Location");
            if (location) {
                // 处理相对路径
                if (location.indexOf("http") !== 0) {
                    var baseUrl = url.substring(0, url.indexOf("/", 8)); // 获取协议和域名部分
                    if (location.indexOf("/") === 0) {
                        location = baseUrl + location;
                    } else {
                        location = baseUrl + "/" + location;
                    }
                }
                logger.debug("重定向到: " + location);
                return location;
            }
        }
        // 如果没有重定向或无法获取Location头，返回原URL
        logger.debug("无需重定向或无法获取重定向信息");
        return url;
    } catch (e) {
        logger.warn("获取真实链接失败: " + e.message);
        return url;
    }
}

/**
 * 从URL中提取track_id
 * @param {string} url - URL字符串
 * @returns {string|null} track_id
 */
function extractTrackId(url) {
    var match = url.match(/track_id=(\d+)/);
    return match ? match[1] : null;
}

/**
 * URL解码
 * @param {string} str - 编码的字符串
 * @returns {string} 解码后的字符串
 */
function unquote(str) {
    try {
        return decodeURIComponent(str);
    } catch (e) {
        return str;
    }
}

/**
 * 格式化时间标签（毫秒转LRC格式）
 * @param {number} startMs - 开始时间（毫秒）
 * @returns {string} LRC格式时间标签 [mm:ss.fff]
 */
function formatTimeTag(startMs) {
    var minutes = Math.floor(startMs / 60000);
    var seconds = Math.floor((startMs % 60000) / 1000);
    var milliseconds = startMs % 1000;

    var minutesStr = (minutes < 10 ? "0" : "") + minutes;
    var secondsStr = (seconds < 10 ? "0" : "") + seconds;
    var millisecondsStr = (milliseconds < 10 ? "00" : (milliseconds < 100 ? "0" : "")) + milliseconds;

    return "[" + minutesStr + ":" + secondsStr + "." + millisecondsStr + "]";
}

/**
 * 解析单个文件下载链接
 * @param {ShareLinkInfo} shareLinkInfo - 分享链接信息
 * @param {JsHttpClient} http - HTTP客户端
 * @param {JsLogger} logger - 日志记录器
 * @returns {string} 下载链接
 */
function parse(shareLinkInfo, http, logger) {
    logger.info("===== 开始解析汽水音乐 =====");

    try {
        // 优先从ShareKey获取track_id（最快方式）
        var trackId = shareLinkInfo.getShareKey();

        // 如果ShareKey为空，尝试从URL中提取
        if (!trackId) {
            var shareUrl = shareLinkInfo.getShareUrl();
            logger.info("分享URL: " + shareUrl);

            if (shareUrl) {
                // 先尝试直接从URL提取track_id（避免重定向超时）
                trackId = extractTrackId(shareUrl);

                // 如果是短链接且仍未提取到track_id，才进行重定向处理
                if (!trackId && shareUrl.indexOf("qishui.douyin.com") !== -1) {
                    logger.info("检测到短链接，尝试获取真实URL...");
                    try {
                        shareUrl = getRealUrl(shareUrl, http, logger);
                        logger.info("重定向后URL: " + shareUrl);
                        trackId = extractTrackId(shareUrl);
                    } catch (e) {
                        logger.warn("短链接重定向处理失败: " + e.message);
                    }
                }
            }
        }

        logger.info("歌曲ID: " + trackId);

        if (!trackId) {
            throw new Error("无法提取track_id");
        }

        // 设置必要的浏览器请求头（最小化，避免触发反爬虫）
        http.putHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        http.putHeader("Accept-Language", "zh-CN,zh;q=0.9");
        http.putHeader("Referer", "https://music.douyin.com/");
        http.putHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // 请求音乐页面
        var musicUrl = "https://music.douyin.com/qishui/share/track?track_id=" + trackId;
        logger.info("请求音乐页面: " + musicUrl);
        logger.debug("开始请求，请等待...");

        // 使用getWithRedirect自动处理重定向
        // 注意：如果超时，可能是网络问题或目标网站响应慢
        var response = http.getWithRedirect(musicUrl);

        logger.debug("请求完成，状态码: " + response.statusCode());

        if (response.statusCode() !== 200) {
            throw new Error("获取页面内容失败，状态码: " + response.statusCode());
        }

        var htmlContent = response.body();

        if (!htmlContent) {
            throw new Error("页面内容为空");
        }

        logger.debug("页面内容长度: " + htmlContent.length);

        // 初始化结果
        var musicPlayUrl = "";

        // 提取 _ROUTER_DATA 数据（音频地址和歌词）
        // 匹配模式：<script async="" data-script-src="modern-inline">_ROUTER_DATA = {...};
        var routerDataPattern = /<script\s+async=""\s+data-script-src="modern-inline">\s*_ROUTER_DATA\s*=\s*({[\s\S]*?});/;
        var routerDataMatch = htmlContent.match(routerDataPattern);

        if (routerDataMatch) {
            try {
                var jsonStr = routerDataMatch[1].trim();
                var jsonData = JSON.parse(jsonStr);

                logger.debug("解析_ROUTER_DATA成功");

                // 提取音频URL
                var audioOption = jsonData.loaderData &&
                                 jsonData.loaderData.track_page &&
                                 jsonData.loaderData.track_page.audioWithLyricsOption;

                if (audioOption && audioOption.url) {
                    musicPlayUrl = audioOption.url;
                    logger.info("提取到音频URL: " + musicPlayUrl);
                }

                // 提取歌词（可选，用于日志）
                if (audioOption && audioOption.lyrics && audioOption.lyrics.sentences) {
                    var sentences = audioOption.lyrics.sentences;
                    logger.debug("提取到歌词，共 " + sentences.length + " 句");
                }

            } catch (e) {
                logger.warn("解析_ROUTER_DATA失败: " + e.message);
            }
        } else {
            logger.warn("未找到_ROUTER_DATA");
        }

        // 如果未找到音频URL，尝试从application/ld+json中提取（备用方案）
        if (!musicPlayUrl) {
            logger.warn("未从_ROUTER_DATA中提取到音频URL，尝试备用方案");

            // 提取 application/ld+json 数据
            var ldJsonPattern = /<script\s+data-react-helmet="true"\s+type="application\/ld\+json">([\s\S]*?)<\/script>/;
            var ldJsonMatch = htmlContent.match(ldJsonPattern);

            if (ldJsonMatch) {
                try {
                    var ldJsonStr = unquote(ldJsonMatch[1]);
                    var ldJsonData = JSON.parse(ldJsonStr);
                    logger.debug("解析ld+json成功，标题: " + (ldJsonData.title || "无"));
                } catch (e) {
                    logger.warn("解析ld+json失败: " + e.message);
                }
            }
        }

        if (!musicPlayUrl) {
            throw new Error("没有找到相关音乐");
        }

        logger.info("解析成功: " + musicPlayUrl);
        return musicPlayUrl;

    } catch (e) {
        logger.error("解析失败: " + e.message);
        throw e;
    }
}
