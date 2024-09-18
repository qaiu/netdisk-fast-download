package cn.qaiu.lz.common.cache;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.templates.SqlTemplate;

import java.util.HashMap;
import java.util.Map;

public class CacheManager {
    private final JDBCPool jdbcPool = JDBCPoolInit.instance().getPool();


    public Future<CacheLinkInfo> get(String cacheKey) {
        String sql = "SELECT share_key as shareKey, direct_link as directLink, expiration FROM cache_link_info WHERE share_key = #{share_key}";
        Map<String, Object> params = new HashMap<>();
        params.put("share_key", cacheKey);
        Promise<CacheLinkInfo> promise = Promise.promise();
        SqlTemplate.forQuery(jdbcPool, sql)
                .mapTo(CacheLinkInfo.class)
                .execute(params)
                .onSuccess(rows->{
                    CacheLinkInfo cacheHit;
                    if (rows.size() > 0) {
                        cacheHit = rows.iterator().next();
                        cacheHit.setCacheHit(true);
                    } else {
                        cacheHit = new CacheLinkInfo(JsonObject.of("cacheHit", false, "shareKey", cacheKey));
                    }
                    promise.complete(cacheHit);
                }).onFailure(Throwable::printStackTrace);
        return promise.future();
    }


    // 插入或更新缓存数据
    public Future<Void> cacheShareLink(CacheLinkInfo cacheLinkInfo) {
        String sql = "MERGE INTO cache_link_info (share_key, direct_link, expiration) " +
                "KEY (share_key) " +
                "VALUES (#{shareKey}, #{directLink}, #{expiration})";

        // 直接传递 CacheLinkInfo 实体类
        return SqlTemplate.forUpdate(jdbcPool, sql)
                .mapFrom(CacheLinkInfo.class) // 将实体类映射为 Tuple 参数
                .execute(cacheLinkInfo)
                .mapEmpty();
    }

    // 统计网盘厂商API解析次数
    public Future<Integer> updateTotalByCached(String shareKey) {
        Promise<Integer> promise = Promise.promise();
        String sql = """
                MERGE INTO `api_statistics_info` (`pan_type`, `share_key`, `cache_hit_total`, `update_ts`)
                                KEY (`share_key`)
                                VALUES (#{panType}, #{shareKey}, #{total}, #{ts})
                """;

        getShareKeyTotal(shareKey, "cache_hit_total").onSuccess(total -> {
            Integer newTotal = (total == null ? 0 : total) + 1;
            SqlTemplate.forUpdate(jdbcPool, sql)
                    .execute(new HashMap<>() {{
                        put("panType", getShareType(shareKey));
                        put("shareKey", shareKey);
                        put("total", newTotal);
                        put("ts", System.currentTimeMillis());
                    }})
                    .onSuccess(res -> promise.complete(res.rowCount()))
                    .onFailure(Throwable::printStackTrace);
        });
        return promise.future();
    }


    private String getShareType(String fullShareKey) {
        // 将type和shareKey组合成一个字符串作为缓存key
        return fullShareKey.split(":")[0];
    }

    // 统计网盘厂商API解析次数
    public Future<Integer> updateTotalByParser(String shareKey) {
        Promise<Integer> promise = Promise.promise();
        String sql = """
                MERGE INTO `api_statistics_info` (`pan_type`, `share_key`, `api_parser_total`, `update_ts`)
                                KEY (`share_key`)
                                VALUES (#{panType}, #{shareKey}, #{total}, #{ts})
                """;

        getShareKeyTotal(shareKey, "api_parser_total").onSuccess(total -> {
            Integer newTotal = (total == null ? 0 : total) + 1;
            SqlTemplate.forUpdate(jdbcPool, sql)
                    .execute(new HashMap<>() {{
                        put("panType", getShareType(shareKey));
                        put("shareKey", shareKey);
                        put("total", newTotal);
                        put("ts", System.currentTimeMillis());
                    }})
                    .onSuccess(res -> promise.complete(res.rowCount()))
                    .onFailure(Throwable::printStackTrace);
        });
        return promise.future();
    }

    public Future<Integer> getShareKeyTotal(String shareKey, String name) {
        String sql = """
                select `share_key`, sum({total_name}) sum_num
                from `api_statistics_info`
                    group by `share_key` having `share_key` = #{shareKey};
                """.replace("{total_name}", name);
        Promise<Integer> promise = Promise.promise();
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("shareKey", shareKey);
        SqlTemplate.forQuery(jdbcPool, sql)
                .mapTo(Row::toJson)
                .execute(paramMap)
                .onSuccess(res -> {
                    Integer total = res.iterator().hasNext() ?
                            res.iterator().next().getInteger("sum_num") : null;
                    promise.complete(total);
                });
        return promise.future();
    }


}
