package cn.qaiu.lz.common.cache;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.db.pool.JDBCType;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.model.PanFileInfo;
import cn.qaiu.lz.web.model.PanFileInfoRowMapper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.templates.SqlTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CacheManager {
    private final Pool jdbcPool = JDBCPoolInit.instance().getPool();
    private final JDBCType jdbcType = JDBCPoolInit.instance().getType();

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManager.class);

    public Future<CacheLinkInfo> get(String cacheKey) {
        String sql = "SELECT share_key as shareKey, direct_link as directLink, expiration FROM cache_link_info WHERE share_key = #{share_key}";
        String sql2 = "SELECT * FROM pan_file_info WHERE share_key = #{share_key}";
        Map<String, Object> params = new HashMap<>();
        params.put("share_key", cacheKey);
        Promise<CacheLinkInfo> promise = Promise.promise();

        Future<RowSet<PanFileInfo>> execute = SqlTemplate.forQuery(jdbcPool, sql2)
                .mapTo(PanFileInfoRowMapper.INSTANCE)
                .execute(params);
        SqlTemplate.forQuery(jdbcPool, sql)
                .mapTo(CacheLinkInfo.class)
                .execute(params)
                .onSuccess(rows->{
                    CacheLinkInfo cacheHit;
                    if (rows.size() > 0) {
                        cacheHit = rows.iterator().next();
                        cacheHit.setCacheHit(true);
                        execute.onSuccess(r2 -> {
                            if (r2.size() > 0) {
                                cacheHit.setFileInfo(r2.iterator().next().toFileInfo());
                            }
                            promise.complete(cacheHit);
                        }).onFailure(e -> {
                            promise.complete(cacheHit);
                        });
                    } else {
                        cacheHit = new CacheLinkInfo(JsonObject.of("cacheHit", false, "shareKey", cacheKey));
                        promise.complete(cacheHit);
                    }
                }).onFailure(e->{
                    promise.fail(e);
                    LOGGER.error("cache get:", e);
                });
        return promise.future();
    }


    // 插入或更新缓存数据
    public void cacheShareLink(CacheLinkInfo cacheLinkInfo) {
        String sql;
        if (jdbcType == JDBCType.MySQL) {
            sql = """
                INSERT INTO cache_link_info (share_key, direct_link, expiration)
                VALUES (#{shareKey}, #{directLink}, #{expiration})
                ON DUPLICATE KEY UPDATE
                    direct_link = VALUES(direct_link),
                    expiration = VALUES(expiration);
                """;
        } else {  // 运行H2
            sql = "MERGE INTO cache_link_info (share_key, direct_link, expiration) " +
                    "KEY (share_key) " +
                    "VALUES (#{shareKey}, #{directLink}, #{expiration})";
        }

        // 直接传递 CacheLinkInfo 实体类
        SqlTemplate.forUpdate(jdbcPool, sql)
                .mapFrom(CacheLinkInfo.class) // 将实体类映射为 Tuple 参数
                .execute(cacheLinkInfo).onSuccess(result -> {
                    if (result.rowCount() > 0) {
                        LOGGER.debug("Cache link info updated for shareKey: {}", cacheLinkInfo.getShareKey());
                    } else {
                        LOGGER.warn("No rows affected when updating cache link info for shareKey: {}", cacheLinkInfo.getShareKey());
                    }
                }).onFailure(Throwable::printStackTrace);

        if (cacheLinkInfo.getFileInfo() != null) {
            String sql2 = """
                    INSERT IGNORE INTO pan_file_info (
                        share_key, file_name, file_id, file_icon, size, size_str, file_type,
                        file_path, create_time, update_time, create_by, description, download_count,
                        pan_type, parser_url, preview_url, hash
                    ) VALUES (
                        #{shareKey}, #{fileName}, #{fileId}, #{fileIcon}, #{size}, #{sizeStr}, #{fileType},
                        #{filePath}, #{createTime}, #{updateTime}, #{createBy}, #{description}, #{downloadCount},
                        #{panType}, #{parserUrl}, #{previewUrl}, #{hash}
                    );
                    """;
            // 判断文件信息是否缓存
            SqlTemplate
                    .forQuery(jdbcPool, "SELECT count(1) AS count FROM pan_file_info WHERE share_key = #{share_key};")
                    .mapTo(Row::toJson)
                    .execute(Collections.singletonMap("share_key", cacheLinkInfo.getShareKey()))
                    .onSuccess(rows -> {
                        JsonObject row = rows.iterator().next();
                        int count = row.getInteger("count");
                        if (count == 0) {
                            // 没有缓存，执行插入
                            PanFileInfo fileInfo = PanFileInfo.fromFileInfo(cacheLinkInfo.getFileInfo());
                            fileInfo.setShareKey(cacheLinkInfo.getShareKey());
                            SqlTemplate.forUpdate(jdbcPool, sql2)
                                    .mapFrom(PanFileInfo.class) // 将实体类映射为 Tuple 参数
                                    .execute(fileInfo).onSuccess(result -> {
                                        if (result.rowCount() > 0) {
                                            LOGGER.debug("Pan file info inserted for shareKey: {}", cacheLinkInfo.getShareKey());
                                        } else {
                                            LOGGER.warn("No rows affected when inserting pan file info for shareKey: {}", cacheLinkInfo.getShareKey());
                                        }
                                    }).onFailure(Throwable::printStackTrace);
                        }
                    });
        }
    }

    // 写入网盘厂商API解析次数
    public Future<Integer> updateTotalByField(String shareKey, CacheTotalField field) {
        Promise<Integer> promise = Promise.promise();
        String fieldLower = field.name().toLowerCase();
        String sql;
        if (jdbcType == JDBCType.MySQL) {  // 假设你有一个标识当前数据库类型的布尔变量
            sql = """
                INSERT INTO `api_statistics_info` (`pan_type`, `share_key`, `{field}`, `update_ts`)
                VALUES (#{panType}, #{shareKey}, #{total}, #{ts})
                ON DUPLICATE KEY UPDATE
                    `pan_type` = VALUES(`pan_type`),
                    `{field}` = VALUES(`{field}`),
                    `update_ts` = VALUES(`update_ts`);
                """.replace("{field}", fieldLower);
        } else {  // 运行H2
            sql = """
                MERGE INTO `api_statistics_info` (`pan_type`, `share_key`, `{field}`, `update_ts`)
                                KEY (`share_key`)
                                VALUES (#{panType}, #{shareKey}, #{total}, #{ts})
                """.replace("{field}", fieldLower);
        }

        getShareKeyTotal(shareKey, fieldLower).onSuccess(total -> {
            Integer newTotal = (total == null ? 0 : total) + 1;
            SqlTemplate.forUpdate(jdbcPool, sql)
                    .execute(new HashMap<>() {{
                        put("panType", getShareType(shareKey));
                        put("shareKey", shareKey);
                        put("total", newTotal);
                        put("ts", System.currentTimeMillis());
                    }})
                    .onSuccess(res -> promise.complete(res.rowCount()))
                    .onFailure(e->{
                        promise.fail(e);
                        LOGGER.error("updateTotalByField: ", e);
                    });
        });
        return promise.future();
    }


    private String getShareType(String fullShareKey) {
        // 将type和shareKey组合成一个字符串作为缓存key
        return fullShareKey.split(":")[0];
    }

    public Future<Integer> getShareKeyTotal(String shareKey, String name) {
        String sql = """
                SELECT `share_key`, SUM({total_name}) AS sum_num
                FROM `api_statistics_info`
                WHERE `share_key` = #{shareKey}
                GROUP BY `share_key`;
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
                }).onFailure(e->{
                    promise.fail(e);
                    LOGGER.error("getShareKeyTotal: ", e);
                });
        return promise.future();
    }

    public Future<Map<String, Integer>> getShareKeyTotal(String shareKey) {
        String sql = """
                SELECT `share_key`, SUM(cache_hit_total) AS hit_total, SUM(api_parser_total) AS parser_total
                FROM `api_statistics_info`
                WHERE `share_key` = #{shareKey}
                GROUP BY `share_key`;
                """;

        Promise<Map<String, Integer>> promise = Promise.promise();
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("shareKey", shareKey);
        SqlTemplate.forQuery(jdbcPool, sql)
                .mapTo(Row::toJson)
                .execute(paramMap)
                .onSuccess(res -> {
                    if(res.iterator().hasNext()) {
                        JsonObject next = res.iterator().next();
                        Map<String, Integer> resp = new HashMap<>(){{
                            put("hit_total" ,next.getInteger("hit_total"));
                            put("parser_total" ,next.getInteger("parser_total"));
                        }};
                        promise.complete(resp);
                    } else {
                        promise.complete();
                    }
                }).onFailure(e->{
                    promise.fail(e);
                    LOGGER.error("getShareKeyTotal0: ", e);
                });
        return promise.future();
    }

}
