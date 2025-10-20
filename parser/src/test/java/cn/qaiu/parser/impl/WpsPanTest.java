package cn.qaiu.parser.impl;

import org.junit.Test;

import cn.qaiu.parser.ParserCreate;
import io.vertx.core.json.JsonObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WPS 云文档解析测试
 */
public class WpsPanTest {

    @Test
    public void testWpsDownload() throws InterruptedException {
        System.out.println("======= WPS 云文档解析测试 =======");
        
        // 测试链接：reset_navicat_mac
        String wpsUrl = "https://www.kdocs.cn/l/ck0azivLlDi3";
        
        System.out.println("测试链接: " + wpsUrl);
        System.out.println("文件名称: reset_navicat_mac");
        System.out.println();
        
        // 使用 ParserCreate 方式创建解析器
        ParserCreate parserCreate = ParserCreate.fromShareUrl(wpsUrl);
        
        System.out.println("解析器类型: " + parserCreate.getShareLinkInfo().getType());
        System.out.println("网盘名称: " + parserCreate.getShareLinkInfo().getPanName());
        System.out.println("分享Key: " + parserCreate.getShareLinkInfo().getShareKey());
        System.out.println("标准URL: " + parserCreate.getShareLinkInfo().getStandardUrl());
        System.out.println();
        
        System.out.println("开始解析下载链接...");
        
        // 创建工具并解析
        parserCreate.createTool()
                .parse()
                .onSuccess(downloadUrl -> {
                    System.out.println("✓ 解析成功!");
                    System.out.println("下载直链: " + downloadUrl);
                    System.out.println();
                    
                    // 解析文件信息
                    JsonObject fileInfo = getFileInfo(downloadUrl);
                    System.out.println("文件信息: " + fileInfo.encodePrettily());
                    System.out.println();
                })
                .onFailure(error -> {
                    System.err.println("✗ 解析失败!");
                    System.err.println("错误信息: " + error.getMessage());
                    error.printStackTrace();
                });
        
        // 等待异步结果
        System.out.println("等待解析结果...");
        TimeUnit.SECONDS.sleep(10);
        
        System.out.println("======= 测试结束 =======");
    }
    
    @Test
    public void testWpsWithShareKey() throws InterruptedException {
        System.out.println("======= WPS 云文档解析测试 (使用 shareKey) =======");
        
        String shareKey = "ck0azivLlDi3";
        
        System.out.println("分享Key: " + shareKey);
        System.out.println();
        
        // 使用 fromType + shareKey 方式
        ParserCreate parserCreate = ParserCreate.fromType("pwps")
                .shareKey(shareKey);
        
        System.out.println("解析器类型: " + parserCreate.getShareLinkInfo().getType());
        System.out.println("网盘名称: " + parserCreate.getShareLinkInfo().getPanName());
        System.out.println("标准URL: " + parserCreate.getShareLinkInfo().getStandardUrl());
        System.out.println();
        
        System.out.println("开始解析下载链接...");
        
        // 创建工具并解析
        parserCreate.createTool()
                .parse()
                .onSuccess(downloadUrl -> {
                    System.out.println("✓ 解析成功!");
                    System.out.println("下载直链: " + downloadUrl);
                    System.out.println();
                    
                    // 解析文件信息
                    JsonObject fileInfo = getFileInfo(downloadUrl);
                    System.out.println("文件信息: " + fileInfo.encodePrettily());
                    System.out.println();
                })
                .onFailure(error -> {
                    System.err.println("✗ 解析失败!");
                    System.err.println("错误信息: " + error.getMessage());
                    error.printStackTrace();
                });
        
        // 等待异步结果
        System.out.println("等待解析结果...");
        TimeUnit.SECONDS.sleep(10);
        
        System.out.println("======= 测试结束 =======");
    }
    
    /**
     * 从 WPS 下载直链中提取文件信息
     * 示例链接: https://hwc-bj.ag.kdocs.cn/api/object/xxx/compatible?response-content-disposition=attachment%3Bfilename%2A%3Dutf-8%27%27reset_navicat_mac.sh&AccessKeyId=xxx&Expires=1760928746&Signature=xxx
     * 
     * @param downloadUrl WPS 下载直链
     * @return JSON 格式的文件信息 {fileName: "reset_navicat_mac.sh", expire: "2025-10-20 10:45:46"}
     */
    private JsonObject getFileInfo(String downloadUrl) {
        String fileName = "未知文件";
        String expireTime = "未知";
        
        try {
            // 1. 提取文件名 - 从 response-content-disposition 参数中提取
            // 格式: attachment%3Bfilename%2A%3Dutf-8%27%27reset_navicat_mac.sh
            // 解码后: attachment;filename*=utf-8''reset_navicat_mac.sh
            Pattern fileNamePattern = Pattern.compile("filename[^=]*=(?:utf-8'')?([^&]+)");
            Matcher fileNameMatcher = fileNamePattern.matcher(URLDecoder.decode(downloadUrl, StandardCharsets.UTF_8));
            if (fileNameMatcher.find()) {
                fileName = fileNameMatcher.group(1);
                // 再次解码（可能被双重编码）
                fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            }
            
            // 2. 提取有效期 - 从 Expires 参数中提取 Unix timestamp
            Pattern expiresPattern = Pattern.compile("[?&]Expires=([0-9]+)");
            Matcher expiresMatcher = expiresPattern.matcher(downloadUrl);
            if (expiresMatcher.find()) {
                long timestamp = Long.parseLong(expiresMatcher.group(1));
                // 转换为日期格式 yyyy-MM-dd HH:mm:ss
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                expireTime = sdf.format(new Date(timestamp * 1000L)); // Unix timestamp 是秒，需要转毫秒
            }
            
        } catch (Exception e) {
            System.err.println("解析文件信息失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return JsonObject.of("fileName", fileName, "expire", expireTime);
    }
}

