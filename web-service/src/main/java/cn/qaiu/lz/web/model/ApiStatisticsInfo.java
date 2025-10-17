package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Length;
import cn.qaiu.db.ddl.Table;
import cn.qaiu.lz.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2024/9/11 16:06
 */
@Table(value = "api_statistics_info", keyFields = "share_key")
@Data
@DataObject
@NoArgsConstructor
public class ApiStatisticsInfo implements ToJson {

    /**
     * pan type 单独拿出来便于统计.
     */
    @Length(varcharSize = 16)
    private String panType;

    /**
     * 分享key type:key
     */
    @Length(varcharSize = 1024)
    private String shareKey;

    /**
     * 命中缓存次数
     */
    private Integer cacheHitTotal;

    /**
     * api解析次数
     */
    private Integer apiParserTotal;

    /**
     * 更新时间戳
     */
    private Long updateTs;

    // 使用 JsonObject 构造
    public ApiStatisticsInfo(JsonObject json) {
        if (json.containsKey("panType")) {
            this.setPanType(json.getString("panType"));
        }
        if (json.containsKey("shareKey")) {
            this.setShareKey(json.getString("shareKey"));
        }
        if (json.containsKey("cacheHitTotal")) {
            this.setCacheHitTotal(json.getInteger("cacheHitTotal"));
        }
        if (json.containsKey("apiParserTotal")) {
            this.setApiParserTotal(json.getInteger("apiParserTotal"));
        }
        if (json.containsKey("updateTs")) {
            this.setUpdateTs(json.getLong("updateTs"));
        }
    }


}
