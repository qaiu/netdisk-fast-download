package cn.qaiu.vx.core.model;


import cn.qaiu.vx.core.util.CastUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 响应实体 用于和前端交互
 * <br>Create date 2021-05-06 09:20:37
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class JsonResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int SUCCESS_CODE = 200;

    private static final int FAIL_CODE = 500;

    private static final String SUCCESS_MESSAGE = "success";

    private static final String FAIL_MESSAGE = "failed";

    private int code = SUCCESS_CODE;//状态码

    private String msg = SUCCESS_MESSAGE;//消息

    private boolean success = true; //是否成功

    private T data;

    private long timestamp = System.currentTimeMillis(); //时间戳

    public JsonResult() {
    }

    public JsonResult(T data) {
        this.data = data;
    }

    public JsonResult(int code, String msg, boolean success, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.success = success;
    }

    public int getCode() {
        return code;
    }

    public JsonResult<T> setCode(int code) {
        this.code = code;
        return this;
    }

    public String getMsg() {
        return msg;
    }

    public JsonResult<T> setMsg(String msg) {
        this.msg = msg;
        return this;
    }

    public T getData() {
        return data;
    }

    public boolean getSuccess() {
        return success;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public JsonResult<T> setData(T data) {
        this.data = data;
        return this;
    }

    public JsonResult<T> setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public JsonResult<T> setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    // 响应失败消息
    public static <T> JsonResult<T> error() {
        return new JsonResult<>(FAIL_CODE, FAIL_MESSAGE, false, null);
    }

    // 响应失败消息
    public static <T> JsonResult<T> error(String msg) {
        if (StringUtils.isEmpty(msg)) msg = FAIL_MESSAGE;
        return new JsonResult<>(FAIL_CODE, msg, false, null);
    }

    // 响应失败消息和状态码
    public static <T> JsonResult<T> error(String msg, int code) {
        if (StringUtils.isEmpty(msg)) msg = FAIL_MESSAGE;
        return new JsonResult<>(code, msg, false, null);
    }

    // 响应成功消息和数据实体
    public static <T> JsonResult<T> data(String msg, T data) {
        if (StringUtils.isEmpty(msg)) msg = SUCCESS_MESSAGE;
        return new JsonResult<>(SUCCESS_CODE, msg, true, data);
    }

    // 响应数据实体
    public static <T> JsonResult<T> data(T data) {
        return new JsonResult<>(SUCCESS_CODE, SUCCESS_MESSAGE, true, data);
    }

    // 响应成功消息
    public static <T> JsonResult<T> success(String msg) {
        if (StringUtils.isEmpty(msg)) msg = SUCCESS_MESSAGE;
        return new JsonResult<>(SUCCESS_CODE, msg, true, null);
    }

    // 响应成功消息
    public static <T> JsonResult<T> success() {
        return new JsonResult<>(SUCCESS_CODE, SUCCESS_MESSAGE, true, null);
    }

    // 转为json对象
    public JsonObject toJsonObject() {
        return JsonObject.mapFrom(this);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    // 转为json对象
    public static JsonResult<?> toJsonResult(JsonObject json) {
        return CastUtil.cast(json.mapTo(JsonResult.class));
    }
}
