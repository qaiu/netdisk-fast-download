package cn.qaiu.lz.web.http;


import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;

@RouteHandler(value = "/v2", order = 10)
@Slf4j
public class ParserApi {

    private final UserService userService = AsyncServiceUtil.getAsyncServiceInstance(UserService.class);
    private final DbService dbService = AsyncServiceUtil.getAsyncServiceInstance(DbService.class);

    @RouteMapping(value = "/login", method = RouteMethod.POST)
    public Future<SysUser> login(SysUser user) {
        log.info("<------- login: {}", user.getUsername());
        return userService.login(user);
    }

    @RouteMapping(value = "/statisticsInfo", method = RouteMethod.GET, order = 99)
    public Future<StatisticsInfo> statisticsInfo() {
        return dbService.getStatisticsInfo();
    }
}
