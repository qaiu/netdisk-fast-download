package cn.com.yhinfo.real;

import cn.com.yhinfo.core.Deploy;
import io.vertx.core.json.JsonObject;


/**
 * 程序入口
 * <br>Create date 2021-05-08 13:00:01
 *
 * @author qiu
 */
public class AppMain {

    public static void main(String[] args) {
        // 注册枚举类型转换器
        Deploy.instance().start(args, AppMain::exec);
    }

    /**
     *
     * @param jsonObject 配置
     */
    private static void exec(JsonObject jsonObject) {
        //
    }


}
