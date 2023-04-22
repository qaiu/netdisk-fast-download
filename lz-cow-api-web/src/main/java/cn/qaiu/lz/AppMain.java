package cn.qaiu.lz;

import cn.qaiu.vx.core.Deploy;
import cn.qaiu.vx.core.util.VertxHolder;
import io.vertx.core.json.JsonObject;
import lombok.val;


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
    }


}
