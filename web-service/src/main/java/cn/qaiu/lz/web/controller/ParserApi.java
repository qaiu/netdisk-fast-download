package cn.qaiu.lz.web.controller;


import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.LinkInfoResp;
import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.ResponseUtil;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.HostAndPort;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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

    private final CacheManager cacheManager = new CacheManager();

    @RouteMapping(value = "/linkInfo", method = RouteMethod.GET)
    public Future<LinkInfoResp> parse(HttpServerRequest request, String pwd) {
        Promise<LinkInfoResp> promise = Promise.promise();
        String url = URLParamUtil.parserParams(request);
        ShareLinkInfo shareLinkInfo = ParserCreate.fromShareUrl(url).setShareLinkInfoPwd(pwd).getShareLinkInfo();
        LinkInfoResp build = LinkInfoResp.builder()
                .downLink(getDownLink(shareLinkInfo, false))
                .apiLink(getDownLink(shareLinkInfo, true))
                .shareLinkInfo(shareLinkInfo).build();
        // 解析次数统计
        cacheManager.getShareKeyTotal(shareLinkInfo.getCacheKey()).onSuccess(res -> {
            if (res != null) {
                build.setCacheHitTotal(res.get("hit_total") == null ? 0: res.get("hit_total"));
                build.setParserTotal(res.get("parser_total") == null ? 0: res.get("parser_total"));
                build.setSumTotal(build.getCacheHitTotal() + build.getParserTotal());
            }
            promise.complete(build);
        }).onFailure(t->{
            t.printStackTrace();
            promise.complete(build);
        });
        return promise.future();
    }

    private static String getDownLink(ShareLinkInfo shareLinkInfo, boolean isJson) {

        String linkPrefix = SharedDataUtil.getJsonConfig("server")
                .getString("domainName");
        if (StringUtils.isBlank(linkPrefix)) {
            linkPrefix = "http://127.0.0.1";
        }
        String pwd = shareLinkInfo.getSharePassword();
        return linkPrefix + (isJson ? "/json/" : "/") + shareLinkInfo.getType() + "/" + shareLinkInfo.getShareKey() +
                (StringUtils.isBlank(pwd) ? "" : ("@" + pwd));
    }

}
