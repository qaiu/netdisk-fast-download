package cn.qaiu.lz.web.service.impl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.model.UserInfo;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.vx.core.annotaions.Service;
import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.templates.SqlTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

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

    @Override
    public Future<StatisticsInfo> getStatisticsInfo() {
        JDBCPool client = JDBCPoolInit.instance().getPool();
        Promise<StatisticsInfo> promise = Promise.promise();
        String sql = """
                select sum(api_parser_total) as parserTotal, sum(cache_hit_total) as cacheTotal,
                sum(api_parser_total) + sum(cache_hit_total) as total
                from api_statistics_info;
                """;

        SqlTemplate.forQuery(client, sql).mapTo(StatisticsInfo.class).execute(new HashMap<>()).onSuccess(row -> {
            StatisticsInfo info;
            if ((info = row.iterator().next()) != null) {
                promise.complete(info);
            } else {
                promise.fail("t_parser_log_info查询为空");
            }
        }).onFailure(e->{
            log.error("getStatisticsInfo: ", e);
            promise.fail(e);
        });
        return promise.future();
    }
}
