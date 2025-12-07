package cn.qaiu.vx.core.verticle.conf;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link cn.qaiu.vx.core.verticle.conf.HttpProxyConf}.
 * NOTE: This class has been automatically generated from the {@link cn.qaiu.vx.core.verticle.conf.HttpProxyConf} original class using Vert.x codegen.
 */
public class HttpProxyConfConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HttpProxyConf obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "password":
          if (member.getValue() instanceof String) {
            obj.setPassword((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "preProxyOptions":
          if (member.getValue() instanceof JsonObject) {
            obj.setPreProxyOptions(new io.vertx.core.net.ProxyOptions((io.vertx.core.json.JsonObject)member.getValue()));
          }
          break;
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).intValue());
          }
          break;
        case "username":
          if (member.getValue() instanceof String) {
            obj.setUsername((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(HttpProxyConf obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HttpProxyConf obj, java.util.Map<String, Object> json) {
    if (obj.getPassword() != null) {
      json.put("password", obj.getPassword());
    }
    if (obj.getPort() != null) {
      json.put("port", obj.getPort());
    }
    if (obj.getPreProxyOptions() != null) {
      json.put("preProxyOptions", obj.getPreProxyOptions().toJson());
    }
    if (obj.getTimeout() != null) {
      json.put("timeout", obj.getTimeout());
    }
    if (obj.getUsername() != null) {
      json.put("username", obj.getUsername());
    }
  }
}
