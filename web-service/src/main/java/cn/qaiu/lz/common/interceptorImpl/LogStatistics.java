package cn.qaiu.lz.common.interceptorImpl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.model.ParserLogInfo;
import cn.qaiu.vx.core.annotaions.HandleSortFilter;
import cn.qaiu.vx.core.interceptor.AfterInterceptor;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.CommonUtil;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.templates.SqlTemplate;
import lombok.extern.slf4j.Slf4j;

import static cn.qaiu.vx.core.util.ConfigConstant.IGNORES_REG;


/**
 * 记录解析日志
 */
@Slf4j
@HandleSortFilter(99)
public class LogStatistics implements AfterInterceptor {

    JDBCPool client = JDBCPoolInit.instance().getPool();
    private final JsonArray ignores = SharedDataUtil.getJsonArrayForCustomConfig(IGNORES_REG);
    @Override
    public void handle(RoutingContext ctx, JsonObject responseData) {

        // 判断是否忽略
        if (CommonUtil.matchRegList(ignores.getList(), ctx.request().path())) {
            return;
        }

        ParserLogInfo parserLogInfo = new ParserLogInfo();
        parserLogInfo.setPath(ctx.request().uri());
        if (responseData == null) {
            String location = ctx.response().headers().get("location");
            if (location != null) {
                parserLogInfo.setCode(200);
                parserLogInfo.setData(location);
            } else {
                log.error("location不存在且responseData为空, path={}", ctx.request().path());
            }
            insert(parserLogInfo);

        } else if (responseData.containsKey("code")) {
            JsonResult<?> result = JsonResult.toJsonResult(responseData);
            parserLogInfo.setCode(result.getCode());
            parserLogInfo.setData(result.getCode() == 500 ? result.getMsg() : result.getData().toString());
            insert(parserLogInfo);
        } else {
            log.error("未知json日志: {}, path: {}", responseData.encode(), ctx.request().path());
        }
    }

    void insert(ParserLogInfo info) {
        SqlTemplate
                .forUpdate(client, "INSERT INTO t_parser_log_info VALUES (#{id},#{logTime},#{path},#{code},#{data})")
                .mapFrom(ParserLogInfo.class)
                .execute(info)
                .onSuccess(res -> {
                    log.info("inserted log: id={}, path={}, code={}", info.getId(), info.getPath(), info.getCode());
                }).onFailure(Throwable::printStackTrace);
    }
}
