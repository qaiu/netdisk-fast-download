package cn.qaiu.parser;//package cn.qaiu.lz.common.parser;

import cn.qaiu.entity.FileInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.List;

public interface IPanTool {
    Future<String> parse();

    default String parseSync() {
        return parse().toCompletionStage().toCompletableFuture().join();
    }

    /**
     * 解析文件列表
     * @return List
     */
    default Future<List<FileInfo>> parseFileList() {
        Promise<List<FileInfo>> promise = Promise.promise();
        promise.complete();
        return promise.future();
    }

    /**
     * 根据文件ID获取下载链接
     * @return url
     */
    default Future<String> parseById() {
        Promise<String> promise = Promise.promise();
        promise.complete();
        return promise.future();
    }
}
