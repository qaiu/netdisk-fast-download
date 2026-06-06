package cn.qaiu.parser;//package cn.qaiu.lz.common.parser;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.clientlink.ClientLinkGeneratorFactory;
import cn.qaiu.parser.clientlink.ClientLinkType;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface IPanTool {

    /** 同步等待超时时间（秒） */
    long SYNC_TIMEOUT_SECONDS = 120;

    /**
     * 解析文件
     * @return 文件内容
     */
    Future<String> parse();

    default String parseSync() {
        return timedJoin(parse());
    }

    /**
     * 解析文件列表
     * @return List
     */
    default Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();
        promise.fail("Not implemented yet");
        return promise.future();
    }

    default List<FileInfo> parseFileListSync() {
        return timedJoin(parseFileList());
    }

    /**
     * 根据文件ID获取下载链接
     * @return url
     */
    default Future<String> parseById() {
        Promise<String> promise = Promise.promise();
        promise.complete("Not implemented yet");
        return promise.future();
    }

    default String parseByIdSync() {
        return timedJoin(parseById());
    }

    /**
     * 解析文件并生成客户端下载链接
     * @return Future<Map<ClientLinkType, String>> 客户端下载链接集合
     */
    default Future<Map<ClientLinkType, String>> parseWithClientLinks() {
        Promise<Map<ClientLinkType, String>> promise = Promise.promise();
        
        // 首先尝试获取 ShareLinkInfo
        ShareLinkInfo shareLinkInfo = getShareLinkInfo();
        if (shareLinkInfo == null) {
            promise.fail("无法获取 ShareLinkInfo");
            return promise.future();
        }
        
        // 检查是否已经有下载链接元数据
        String existingDownloadUrl = (String) shareLinkInfo.getOtherParam().get("downloadUrl");
        if (existingDownloadUrl != null && !existingDownloadUrl.trim().isEmpty()) {
            // 如果已经有下载链接，直接生成客户端链接
            try {
                Map<ClientLinkType, String> clientLinks = 
                    ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
                promise.complete(clientLinks);
                return promise.future();
            } catch (Exception e) {
                // 如果生成失败，继续尝试解析
            }
        }
        
        // 尝试解析获取下载链接
        parse().onComplete(result -> {
            if (result.succeeded()) {
                try {
                    String downloadUrl = result.result();
                    if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
                        // 确保下载链接已存储到 otherParam 中
                        shareLinkInfo.getOtherParam().put("downloadUrl", downloadUrl);
                        
                        // 生成客户端链接
                        Map<ClientLinkType, String> clientLinks = 
                            ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
                        promise.complete(clientLinks);
                    } else {
                        promise.fail("解析结果为空，无法生成客户端链接");
                    }
                } catch (Exception e) {
                    promise.fail("生成客户端链接失败: " + e.getMessage());
                }
            } else {
                // 解析失败时，尝试使用分享链接作为默认下载链接
                try {
                    String fallbackUrl = shareLinkInfo.getShareUrl();
                    if (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) {
                        // 使用分享链接作为默认下载链接
                        shareLinkInfo.getOtherParam().put("downloadUrl", fallbackUrl);
                        
                        // 尝试生成客户端链接
                        Map<ClientLinkType, String> clientLinks = 
                            ClientLinkGeneratorFactory.generateAll(shareLinkInfo);
                        promise.complete(clientLinks);
                    } else {
                        promise.fail("解析失败且无法使用分享链接作为默认下载链接: " + result.cause().getMessage());
                    }
                } catch (Exception e) {
                    promise.fail("解析失败且生成默认客户端链接失败: " + result.cause().getMessage());
                }
            }
        });
        
        return promise.future();
    }

    /**
     * 解析文件并生成客户端下载链接（同步版本）
     * @return Map<ClientLinkType, String> 客户端下载链接集合
     */
    default Map<ClientLinkType, String> parseWithClientLinksSync() {
        return timedJoin(parseWithClientLinks());
    }

    /**
     * 获取 ShareLinkInfo 对象
     * 子类需要实现此方法来提供 ShareLinkInfo
     * @return ShareLinkInfo 对象
     */
    default ShareLinkInfo getShareLinkInfo() {
        return null;
    }

    /**
     * 带超时的同步等待工具方法，替代无超时的 join()
     */
    private static <T> T timedJoin(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture()
                    .get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程被中断", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("同步等待超时（" + SYNC_TIMEOUT_SECONDS + "秒）", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
    }
}
