package cn.com.yhinfo.real.common;

import io.vertx.core.json.JsonObject;

/**
 * sinoreal2-web <br>
 * 实现此接口 POJO转JSON对象
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * <br>Create date 2021/8/27 11:40
 */
public interface ToJson {

    /**
     * POJO转JSON对象
     *
     * @return Json Object
     */
    default JsonObject toJson() {
        return JsonObject.mapFrom(this);
    }
}
