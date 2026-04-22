package cn.qaiu.vx.core.util;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.concurrent.ExecutionException;

public class FutureUtils {

   public static <T> T getResult(Future<T> future) {
       try {
           return future.toCompletionStage().toCompletableFuture().get();
       } catch (InterruptedException | ExecutionException e) {
           throw new RuntimeException(e);
       }
   }
    public static <T> T getResult(Promise<T> promise) {
       return promise.future().toCompletionStage().toCompletableFuture().join();
    }
}
