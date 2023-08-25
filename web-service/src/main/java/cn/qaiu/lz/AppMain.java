package cn.qaiu.lz;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.vx.core.Deploy;
import cn.qaiu.vx.core.util.ConfigConstant;
import cn.qaiu.vx.core.util.VertxHolder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;


/**
 * 程序入口
 * <br>Create date 2021-05-08 13:00:01
 *
 * @author qaiu
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
        WebClientVertxInit.init(VertxHolder.getVertxInstance());
        DatabindCodec.mapper().registerModule(new JavaTimeModule());
        if (jsonObject.getJsonObject(ConfigConstant.SERVER).getBoolean("enableDatabase")) {
            JDBCPoolInit.builder().config(jsonObject.getJsonObject("dataSource")).build().initPool();
        }
    }


}
