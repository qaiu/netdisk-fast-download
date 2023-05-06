package cn.qaiu.lz;

import cn.qaiu.vx.core.Deploy;
import io.vertx.core.json.JsonObject;


/**
 * 程序入口
 * <br>Create date 2021-05-08 13:00:01
 *
 * @author qiu
 */
public class AppMain {

    public static void main(String[] args) {
        Deploy.instance().start(args, AppMain::exec);
    }

    /**
     * 初始化数据库
     *
     * @param jsonObject 配置
     */
    private static void exec(JsonObject jsonObject) {
//        JDBCPoolInit.builder().config(jsonObject.getJsonObject("dataSource")).build().initPool();
    }


}
