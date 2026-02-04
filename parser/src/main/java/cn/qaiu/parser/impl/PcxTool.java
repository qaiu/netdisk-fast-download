package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.FileSizeConverter;
import io.vertx.core.Future;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://passport2.chaoxing.com">超星云盘</a>
 */
public class PcxTool extends PanBase {

    public PcxTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        client.getAbs(shareLinkInfo.getShareUrl())
                .send().onSuccess(res -> {
                    String body = res.bodyAsString();
                    try {
                        // 提取文件信息
                        setFileInfo(body);
                        
                        // 直接用正则提取download链接
                        String download = extractDownloadUrl(body);
                        if (download != null && download.contains("fn=")) {
                            complete(download);
                        } else {
                            fail("获取下载链接失败");
                        }
                    } catch (Exception e) {
                        fail("解析文件信息失败: {}", e.getMessage());
                    }
                }).onFailure(handleFail(shareLinkInfo.getShareUrl()));
        return promise.future();
    }

    /**
     * 从HTML中提取download链接
     */
    private String extractDownloadUrl(String html) {
        // 匹配 'download': 'https://xxx' 或 "download": "https://xxx"
        Pattern pattern = Pattern.compile("['\"]download['\"]\\s*:\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 从HTML中提取文件信息并设置到shareLinkInfo
     */
    private void setFileInfo(String html) {
        try {
            FileInfo fileInfo = new FileInfo();
            
            // 提取文件名：从<title>标签或文件名input
            String fileName = extractByRegex(html, "<title>([^<]+)</title>");
            if (fileName == null) {
                fileName = extractByRegex(html, "<input id=\"filename\" type=\"hidden\" value=\"([^\"]+)\"");
            }
            
            // 提取文件大小：'filesize': 'xxx' 或 "filesize": "xxx"
            String fileSizeStr = extractByRegex(html, "['\"]filesize['\"]\\s*:\\s*['\"]([^'\"]+)['\"]");
            Long fileSize = null;
            if (fileSizeStr != null) {
                try {
                    fileSize = Long.parseLong(fileSizeStr);
                } catch (NumberFormatException ignored) {}
            }
            
            // 提取文件类型/后缀：'suffix': 'xxx' 或 "suffix": "xxx"
            String suffix = extractByRegex(html, "['\"]suffix['\"]\\s*:\\s*['\"]([^'\"]+)['\"]");
            
            // 提取objectId（文件ID）：'objectId': 'xxx' 或 "objectId": "xxx"
            String objectId = extractByRegex(html, "['\"]objectId['\"]\\s*:\\s*['\"]([^'\"]+)['\"]");
            
            // 提取创建者：'creator': 'xxx' 或 "creator": "xxx"
            String creator = extractByRegex(html, "['\"]creator['\"]\\s*:\\s*['\"]([^'\"]+)['\"]");
            
            // 提取上传时间：'uploadDate': timestamp
            String uploadDate = extractByRegex(html, "['\"]uploadDate['\"]\\s*:\\s*(\\d+)");
            
            // 提取缩略图：'thumbnail': 'xxx' 或 "thumbnail": "xxx"
            String thumbnail = extractByRegex(html, "['\"]thumbnail['\"]\\s*:\\s*['\"]([^'\"]+)['\"]");
            
            // 设置文件信息
            if (fileName != null) {
                fileInfo.setFileName(fileName);
            }
            
            if (fileSize != null) {
                fileInfo.setSize(fileSize);
                fileInfo.setSizeStr(FileSizeConverter.convertToReadableSize(fileSize));
            }
            
            if (suffix != null) {
                fileInfo.setFileType(suffix);
            }
            
            if (objectId != null) {
                fileInfo.setFileId(objectId);
            }
            
            if (creator != null) {
                fileInfo.setCreateBy(creator);
            }
            
            if (uploadDate != null) {
                try {
                    long timestamp = Long.parseLong(uploadDate);
                    // 转换为日期格式
                    java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
                    java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, 
                            java.time.ZoneId.systemDefault());
                    fileInfo.setCreateTime(dateTime.format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (NumberFormatException ignored) {}
            }
            
            if (thumbnail != null) {
                fileInfo.setPreviewUrl(thumbnail);
            }
            
            fileInfo.setPanType(shareLinkInfo.getType());
            
            // 将文件信息存储到shareLinkInfo的otherParam中
            shareLinkInfo.getOtherParam().put("fileInfo", fileInfo);
            
        } catch (Exception e) {
            log.warn("提取文件信息失败: {}", e.getMessage());
        }
    }
    
    /**
     * 使用正则表达式提取内容
     */
    private String extractByRegex(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }


//    public static void main(String[] args) {
//        String s = new PcxTool(ShareLinkInfo.newBuilder().shareUrl("https://pan-yz.cldisk.com/external/m/file/953658049102462976")
//                .shareKey("953658049102462976")
//                .build()).parseSync();
//        System.out.println(s);
//    }
}
