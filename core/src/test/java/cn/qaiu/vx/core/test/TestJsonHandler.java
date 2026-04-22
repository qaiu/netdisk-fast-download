package cn.qaiu.vx.core.test;

import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.MIMEType;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.model.JsonResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * 用于测试 RouterHandlerFactory 对 JsonObject/JsonArray 参数绑定的测试 Handler
 */
@RouteHandler("test")
public class TestJsonHandler {

    /** POST /api/test/json-object  Body: {"name":"test","value":123} */
    @RouteMapping(value = "/json-object", method = RouteMethod.POST, requestMIMEType = MIMEType.APPLICATION_JSON)
    public Future<JsonResult> testJsonObject(JsonObject body) {
        // 只返回是否绑定成功及已知字段值，不嵌套原始 body 避免 toJsonObject() 循环
        boolean bound = body != null;
        String nameVal = bound ? body.getString("name", "") : "";
        return Future.succeededFuture(JsonResult.data(new io.vertx.core.json.JsonObject()
                .put("bound", bound)
                .put("name", nameVal)));
    }

    /** POST /api/test/json-array  Body: [1,2,3] */
    @RouteMapping(value = "/json-array", method = RouteMethod.POST, requestMIMEType = MIMEType.APPLICATION_JSON)
    public Future<JsonResult> testJsonArray(JsonArray body) {
        return Future.succeededFuture(JsonResult.data(new io.vertx.core.json.JsonObject()
                .put("bound", body != null)
                .put("size", body != null ? body.size() : -1)));
    }
}
