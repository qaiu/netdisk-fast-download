package cn.qaiu.vx.core.util;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureUtils {

    /** 默认同步等待超时时间（秒） */
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    public static <T> T getResult(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture()
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程被中断", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("等待Future超时（" + DEFAULT_TIMEOUT_SECONDS + "秒）", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause : e);
        }
    }

    public static <T> T getResult(Promise<T> promise) {
        try {
            return promise.future().toCompletionStage().toCompletableFuture()
                    .get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程被中断", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("等待Promise超时（" + DEFAULT_TIMEOUT_SECONDS + "秒）", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException(cause != null ? cause : e);
        }
    }
}
