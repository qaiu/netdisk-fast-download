package cn.qaiu.lz.web.controller;


import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.lz.common.cache.CacheManager;
import cn.qaiu.lz.common.util.URLParamUtil;
import cn.qaiu.lz.web.model.CacheLinkInfo;
import cn.qaiu.lz.web.model.LinkInfoResp;
import cn.qaiu.lz.web.model.StatisticsInfo;
import cn.qaiu.lz.web.service.DbService;
import cn.qaiu.parser.PanDomainTemplate;
import cn.qaiu.parser.ParserCreate;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.RouteMapping;
import cn.qaiu.vx.core.enums.RouteMethod;
import cn.qaiu.vx.core.model.JsonResult;
import cn.qaiu.vx.core.util.AsyncServiceUtil;
import cn.qaiu.vx.core.util.CommonUtil;
import cn.qaiu.vx.core.util.ResponseUtil;
import cn.qaiu.vx.core.util.SharedDataUtil;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RouteHandler(value = "/v2", order = 10)
@Slf4j
public class ParserApi {

    private final DbService dbService = AsyncServiceUtil.getAsyncServiceInstance(DbService.class);


    @RouteMapping(value = "/statisticsInfo", method = RouteMethod.GET, order = 99)
    public Future<StatisticsInfo> statisticsInfo() {
        return dbService.getStatisticsInfo();
    }

    private final CacheManager cacheManager = new CacheManager();
    private final ServerApi serverApi = new ServerApi();

    @RouteMapping(value = "/linkInfo", method = RouteMethod.GET)
    public Future<LinkInfoResp> parse(HttpServerRequest request, String pwd) {
        Promise<LinkInfoResp> promise = Promise.promise();
        String url = URLParamUtil.parserParams(request);
        ParserCreate parserCreate = ParserCreate.fromShareUrl(url).setShareLinkInfoPwd(pwd);
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        LinkInfoResp build = LinkInfoResp.builder()
                .downLink(getDownLink(parserCreate, false))
                .apiLink(getDownLink(parserCreate, true))
                .viewLink(getViewLink(parserCreate))
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

    private static String getViewLink(ParserCreate create) {

        String linkPrefix = SharedDataUtil.getJsonStringForServerConfig("domainName");
        if (StringUtils.isBlank(linkPrefix)) {
            return "";
        }
        return linkPrefix + "/v2/view/" + create.genPathSuffix();
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

    @RouteMapping("/getFileList")
    public Future<List<FileInfo>> getFileList(HttpServerRequest request, String pwd, String dirId, String uuid) {
        String url = URLParamUtil.parserParams(request);
        ParserCreate parserCreate = ParserCreate.fromShareUrl(url).setShareLinkInfoPwd(pwd);
        String linkPrefix = SharedDataUtil.getJsonConfig("server").getString("domainName");
        parserCreate.getShareLinkInfo().getOtherParam().put("domainName", linkPrefix);
        if (StringUtils.isNotBlank(dirId)) {
            parserCreate.getShareLinkInfo().getOtherParam().put("dirId", dirId);
        }
        if (StringUtils.isNotBlank(uuid)) {
            parserCreate.getShareLinkInfo().getOtherParam().put("uuid", uuid);
        }
        return parserCreate.createTool().parseFileList();
    }

    // 目录解析下载文件
    // @RouteMapping("/getFileDownUrl/:type/:param")
    public Future<String> getFileDownUrl(String type, String param) {
        ParserCreate parserCreate = ParserCreate.fromType(type).shareKey("-") // shareKey not null
                .setShareLinkInfoPwd("-");

        if (param.isEmpty()) {
            Promise<String> promise = Promise.promise();
            promise.fail("下载参数为空");
            return promise.future();
        }

        String paramStr = new String(Base64.getDecoder().decode(param));
        ShareLinkInfo shareLinkInfo = parserCreate.getShareLinkInfo();
        shareLinkInfo.getOtherParam().put("paramJson", new JsonObject(paramStr));

        // domainName
        String linkPrefix = SharedDataUtil.getJsonConfig("server").getString("domainName");
        shareLinkInfo.getOtherParam().put("domainName", linkPrefix);
        return parserCreate.createTool().parseById();
    }

    @RouteMapping("/redirectUrl/:type/:param")
    public Future<Void> redirectUrl(HttpServerResponse response, String type, String param) {
        Promise<Void> promise = Promise.promise();

        getFileDownUrl(type, param)
                .onSuccess(res -> ResponseUtil.redirect(response, res))
                .onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }


    /**
     * 预览媒体文件
     */
    @RouteMapping(value = "/view/:type/:key", method = RouteMethod.GET, order = 2)
    public void view(HttpServerRequest request, HttpServerResponse response, String type, String key) {
        String previewURL = SharedDataUtil.getJsonStringForServerConfig("previewURL");
        serverApi.parseKeyJson(request, type, key).onSuccess(res -> {
            redirect(response, previewURL, res);
        }).onFailure(e -> {
            ResponseUtil.fireJsonResultResponse(response, JsonResult.error(e.toString()));
        });
    }

    private static void redirect(HttpServerResponse response, String previewURL, CacheLinkInfo res) {
        String directLink = res.getDirectLink();
        ResponseUtil.redirect(response, previewURL + URLEncoder.encode(directLink, StandardCharsets.UTF_8));
    }

    /**
     * 预览媒体文件-目录预览
     */
    @RouteMapping(value = "/preview", method = RouteMethod.GET, order = 9)
    public void viewURL(HttpServerRequest request, HttpServerResponse response, String pwd) {
        String previewURL = SharedDataUtil.getJsonStringForServerConfig("previewURL");
        new ServerApi().parseJson(request, pwd).onSuccess(res -> {
            redirect(response, previewURL, res);
        }).onFailure(e -> {
            ResponseUtil.fireJsonResultResponse(response, JsonResult.error(e.toString()));
        });
    }


    @RouteMapping("/viewUrl/:type/:param")
    public Future<Void> viewUrl(HttpServerResponse response, String type, String param) {
        Promise<Void> promise = Promise.promise();

        String viewPrefix = SharedDataUtil.getJsonConfig("server").getString("previewURL");
        getFileDownUrl(type, param)
                .onSuccess(res -> {
                    String url = viewPrefix + URLEncoder.encode(res, StandardCharsets.UTF_8);
                    ResponseUtil.redirect(response, url);
                })
                .onFailure(t -> promise.fail(t.fillInStackTrace()));
        return promise.future();
    }

    // 获取版本号
    @RouteMapping("/build-version")
    public String getVersion() {
        return CommonUtil.getAppVersion()
        .replace("-", "")
        .replace("Z", "")
        .replace("T", "_")
        .replace("-", "")
        .replace(":", "");
    }
}
