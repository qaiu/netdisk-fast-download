package cn.qaiu.parser.custompy;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.ParserCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试前端模板代码执行
 * 模拟用户使用 Python 模板
 */
public class PyTemplateCodeTest {
    
    private static final Logger log = LoggerFactory.getLogger(PyTemplateCodeTest.class);
    
    // 这是前端发送的模板代码（与 pyParserTemplate.js 中一致）
    private static final String TEMPLATE_CODE = """
import requests
import re
import json


def parse(share_link_info, http, logger):
    \"\"\"
    解析单个文件下载链接
    
    Args:
        share_link_info: 分享链接信息对象
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        str: 直链下载地址
    \"\"\"
    url = share_link_info.get_share_url()
    logger.info(f"开始解析: {url}")
    
    # 使用 requests 库发起请求（推荐）
    response = requests.get(url, headers={
        "Referer": url
    })
    
    if not response.ok:
        raise Exception(f"请求失败: {response.status_code}")
    
    html = response.text
    
    # 示例：使用正则表达式提取下载链接
    # match = re.search(r'download_url["\\\\':]\s*["\\\\']([^"\\\\'>]+)', html)
    # if match:
    #     return match.group(1)
    
    return "https://example.com/download/file.zip"


def parse_file_list(share_link_info, http, logger):
    \"\"\"
    解析文件列表（可选）
    
    Args:
        share_link_info: 分享链接信息对象
        http: HTTP客户端
        logger: 日志记录器
    
    Returns:
        list: 文件信息列表
    \"\"\"
    dir_id = share_link_info.get_other_param("dirId") or "0"
    logger.info(f"解析文件列表，目录ID: {dir_id}")
    
    file_list = []
    
    return file_list
""";

    public static void main(String[] args) throws Exception {
        log.info("======= 测试前端模板代码执行 =======");
        
        // 测试代码
        log.info("测试代码长度: {} 字符", TEMPLATE_CODE.length());
        log.info("代码前100字符:\n{}", TEMPLATE_CODE.substring(0, Math.min(100, TEMPLATE_CODE.length())));
        
        // 创建 ShareLinkInfo - 使用 example.com 测试 URL
        ParserCreate parserCreate = ParserCreate.fromShareUrl("https://example.com/s/abc");
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        
        // 创建执行器
        PyPlaygroundExecutor executor = new PyPlaygroundExecutor(shareLinkInfo, TEMPLATE_CODE);
        
        // 异步执行
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        log.info("开始执行 Python 代码...");
        
        executor.executeParseAsync()
            .onSuccess(result -> {
                resultRef.set(result);
                latch.countDown();
            })
            .onFailure(e -> {
                errorRef.set(e);
                latch.countDown();
            });
        
        // 等待结果（最多 60 秒）
        if (!latch.await(60, TimeUnit.SECONDS)) {
            log.error("执行超时（60秒）");
            System.exit(1);
        }
        
        // 检查结果
        if (errorRef.get() != null) {
            log.error("执行失败: {}", errorRef.get().getMessage());
            errorRef.get().printStackTrace();
            
            // 打印日志
            log.info("执行日志:");
            for (PyPlaygroundLogger.LogEntry entry : executor.getLogs()) {
                log.info("  [{}] {}", entry.getLevel(), entry.getMessage());
            }
            
            System.exit(1);
        }
        
        log.info("✓ 执行成功，返回: {}", resultRef.get());
        
        // 打印日志
        log.info("执行日志:");
        for (PyPlaygroundLogger.LogEntry entry : executor.getLogs()) {
            log.info("  [{}] {}", entry.getLevel(), entry.getMessage());
        }
    }
}
