package cn.qaiu.lz.web.service.impl;

import cn.qaiu.lz.common.model.UserInfo;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

/**
 * lz-web
 * <br>Create date 2021/7/12 17:26
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Slf4j
@Service
public class DbServiceImpl implements DbService {
    @Override
    public Future<JsonObject> sayOk(String data) {
        log.info("say ok1 -> wait...");
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Future.succeededFuture(JsonObject.mapFrom(JsonResult.data("Hi: " + data)));
    }

    @Override
    public Future<JsonObject> sayOk2(String data, UserInfo holder) {
//        val context = VertxHolder.getVertxInstance().getOrCreateContext();
//        log.info("say ok2 -> " + context.get("username"));
//        log.info("--> {}", holder.toString());
        return Future.succeededFuture(JsonObject.mapFrom(JsonResult.data("Hi: " + data)));
    }
}
