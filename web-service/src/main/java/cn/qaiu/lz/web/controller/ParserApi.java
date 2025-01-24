package cn.qaiu.lz.web.controller;


import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.LinkInfoResp;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
        ParserCreate parserCreate = ParserCreate.fromShareUrl(url).setShareLinkInfoPwd(pwd);
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        LinkInfoResp build = LinkInfoResp.builder()
                .downLink(getDownLink(parserCreate, false))
                .apiLink(getDownLink(parserCreate, true))
                .shareLinkInfo(shareLinkInfo).build();
        // 解析次数统计
        shareLinkInfo.getOtherParam().put("UA",request.headers().get("user-agent"));
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

    private static String getDownLink(ParserCreate create, boolean isJson) {

        String linkPrefix = SharedDataUtil.getJsonConfig("server").getString("domainName");
        if (StringUtils.isBlank(linkPrefix)) {
            linkPrefix = "http://127.0.0.1";
        }
        // 下载短链前缀 /d
        return linkPrefix + (isJson ? "/json/" : "/d/") + create.genPathSuffix();
    }

    /**
     * 获取支持的网盘列表
     * @return list-map: name: 网盘名, type: 网盘标识, url: 网盘域名地址
     */
    @RouteMapping("/getPanList")
    public List<Map<String, String>> getPanList() {
        return Arrays.stream(PanDomainTemplate.values()).map(pan -> new TreeMap<String, String>() {{
            put("name", pan.getDisplayName());
            put("type", pan.name().toLowerCase());
            put("shareUrlFormat", pan.getStandardUrlTemplate());
            put("url", pan.getPanDomain());
        }}).collect(Collectors.toList());
    }

}
