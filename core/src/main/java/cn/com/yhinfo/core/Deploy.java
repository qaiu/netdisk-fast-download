package cn.com.yhinfo.core;

import cn.com.yhinfo.core.util.ConfigUtil;
import cn.com.yhinfo.core.util.VertxHolder;
import cn.com.yhinfo.core.verticle.ReverseProxyVerticle;
import cn.com.yhinfo.core.verticle.ServiceVerticle;
import cn.com.yhinfo.core.verticle.RouterVerticle;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.Calendar;
import java.util.Date;

/**
 * vertx启动类 需要在主启动类完成回调
 * <br>Create date 2021-05-07 10:26:54
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public final class Deploy {

    private static final Deploy INSTANCE = new Deploy();
    private static final Logger LOGGER = LoggerFactory.getLogger(Deploy.class);
    private static final long startTime = System.currentTimeMillis();

    private final Vertx tempVertx = Vertx.vertx();
    StringBuilder path = new StringBuilder("app");

    private JsonObject customConfig;
    private Handler<JsonObject> handle;

    public static Deploy instance() {
        return INSTANCE;
    }

    public void start(String[] args, Handler<JsonObject> handle) {
        this.handle = handle;
        if (args.length > 0) {
            // 启动参数dev或者prod
            path.append("-").append(args[0]);
        }

        // 读取yml配置
        ConfigUtil.readYamlConfig(path.toString(), tempVertx)
                .onSuccess(this::readConf)
                .onFailure(Throwable::printStackTrace);
    }

    private void readConf(JsonObject conf) {
        outLogo(conf);
        String activeMode = conf.getString("active");
        if ("dev".equals(activeMode)) {
            LOGGER.info("---------------> development environment <--------------\n");
            System.setProperty("vertxweb.environment","dev");
        } else {
            LOGGER.info("---------------> Production environment <--------------\n");
        }
        ConfigUtil.readYamlConfig(path + "-" + activeMode, tempVertx).onSuccess(this::deployVerticle);
    }

    /**
     * 打印logo
     */
    private void outLogo(JsonObject conf) {
        Date date = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR);
        String logoTemplete = "\nWeb Server powered by: \n" +
                " ____   ____              _              _    _    \n" +
                "|_^^_| |_^^_|            / |_           | |  | |   \n" +
                "  \\ \\   / /.---.  _ .--.`| |-'   _   __ | |__| |_  \n" +
                "   \\ \\ / // /__\\\\[ `/'`\\]| |    [ \\ [  ]|____   _| \n" +
                "    \\ V / | \\__., | |    | |, _  > '  <     _| |_  \n" +
                "     \\_/   '.__.'[___]   \\__/(_)[__]`\\_]   |_____| \n" +
                "                                      Version: %s; Framework version: %s; %s©%d.\n\n";

        System.out.printf(logoTemplete,
                conf.getString("version_app"),
                conf.getString("version_vertx"),
                conf.getString("copyright"),
                year
        );
    }

    /**
     * 部署Verticle
     *
     * @param globalConfig 配置
     */
    private void deployVerticle(JsonObject globalConfig) {
        tempVertx.close();
        LOGGER.info("配置读取成功");
        customConfig = globalConfig.getJsonObject("custom");

        VertxOptions vertxOptions = new VertxOptions(globalConfig.getJsonObject("vertx"));
        Vertx vertx = Vertx.vertx(vertxOptions);
        VertxHolder.init(vertx);
        //配置保存在共享数据中
        SharedData sharedData = vertx.sharedData();
        LocalMap<String, Object> localMap = sharedData.getLocalMap("local");
        localMap.put("globalConfig", globalConfig);
        localMap.put("customConfig", customConfig);
        localMap.put("server", globalConfig.getJsonObject("server"));
        handle.handle(globalConfig);

        Future<String> future1 = vertx.deployVerticle(RouterVerticle.class, getWorkDeploymentOptions("Router"));
        Future<String> future2 = vertx.deployVerticle(ServiceVerticle.class, getWorkDeploymentOptions("Service"));
        Future<String> future3 = vertx.deployVerticle(ReverseProxyVerticle.class, getWorkDeploymentOptions("proxy"));

        CompositeFuture.all(future1, future2, future3)
                .onSuccess(this::deployWorkVerticalSuccess)
                .onFailure(this::deployVerticalFailed);
    }

    /**
     * 部署失败
     *
     * @param throwable Exception信息
     */
    private void deployVerticalFailed(Throwable throwable) {
        LOGGER.error(throwable.getClass().getName() + ": " + throwable.getMessage());
        System.exit(-1);
    }

    /**
     * 启动时间信息
     *
     * @param compositeFuture future wraps a list
     */
    private void deployWorkVerticalSuccess(CompositeFuture compositeFuture) {
        double t1 = ((double) (System.currentTimeMillis() - startTime)) / 1000;
        double t2 = ((double) System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000;
        LOGGER.info("web服务启动成功 -> 用时: {}s, jvm启动用时: {}s", t1, t2);
    }

    /**
     * deploy Verticle Options
     *
     * @param name the worker pool name
     * @return Deployment Options
     */
    private DeploymentOptions getWorkDeploymentOptions(String name) {
        return getWorkDeploymentOptions(name, customConfig.getInteger("asyncServiceInstances"));
    }

    private DeploymentOptions getWorkDeploymentOptions(String name, int ins) {
        return new DeploymentOptions()
                .setWorkerPoolName(name)
                .setWorker(true)
                .setInstances(ins);
    }

}
