package cn.qaiu.lz.web.model;

import io.vertx.core.json.JsonObject;

// CacheLinkInfoConverter.java
public class CacheLinkInfoConverter {

    public static void fromJson(JsonObject json, CacheLinkInfo obj) {
        if (json.containsKey("shareKey")) {
            obj.setShareKey(json.getString("shareKey"));
        }
        if (json.containsKey("directLink")) {
            obj.setDirectLink(json.getString("directLink"));
        }
        if (json.containsKey("expires")) {
            obj.setExpires(json.getString("expires"));
        }
        if (json.containsKey("expiration")) {
            obj.setExpiration(json.getLong("expiration"));
        }
        obj.setCacheHit(json.getBoolean("cacheHit", false));
    }
}
