package cn.qaiu.lz.common.cache;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.templates.SqlTemplate;

import java.util.HashMap;
import java.util.Map;

public class CacheManager {
    private final JDBCPool jdbcPool = JDBCPoolInit.instance().getPool();


    public Future<CacheLinkInfo> get(String cacheKey) {
        String sql = "SELECT direct_link as directLink, expiration FROM cache_link_info WHERE share_key = #{share_key}";
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
                        cacheHit = new CacheLinkInfo(JsonObject.of("cacheHit", false));
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
}
