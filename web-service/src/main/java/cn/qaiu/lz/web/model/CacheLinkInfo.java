package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Length;
import cn.qaiu.db.ddl.Table;
import cn.qaiu.db.ddl.TableGenIgnore;
import cn.qaiu.entity.FileInfo;
import cn.qaiu.lz.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2024/9/11 16:06
 */
@Table(value = "cache_link_info", keyFields = "share_key")
@Data
@DataObject
@NoArgsConstructor
public class CacheLinkInfo implements ToJson {

    /**
     * 缓存key: type:ShareKey; e.g. lz:xxxx
     */
    @Length(varcharSize = 4096)
    private String shareKey;

    /**
     * 解析后的直链
     */
    @Length(varcharSize = 4096)
    private String directLink;

    /**
     * 是否命中缓存
     */
    @TableGenIgnore
    private Boolean cacheHit;

    /**
     * 到期时间 yyyy-MM-dd hh:mm:ss
     */
    @TableGenIgnore
    private String expires;

    /**
     * 有效期
     */
    private Long expiration;

    private FileInfo fileInfo;


    // 使用 JsonObject 构造
    public CacheLinkInfo(JsonObject json) {
        if (json.containsKey("shareKey")) {
            this.setShareKey(json.getString("shareKey"));
        }
        if (json.containsKey("directLink")) {
            this.setDirectLink(json.getString("directLink"));
        }
        if (json.containsKey("expires")) {
            this.setExpires(json.getString("expires"));
        }
        if (json.containsKey("expiration")) {
            this.setExpiration(json.getLong("expiration"));
        }
        this.setCacheHit(json.getBoolean("cacheHit", false));
    }


}
