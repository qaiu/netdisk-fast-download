package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import cn.qaiu.util.RandomStringGenerator;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.uritemplate.UriTemplate;

/**
 * <a href="https://www.ctfile.com">诚通网盘</a>
 */
public class CtTool extends PanBase {
    private static final String API_URL_PREFIX = "https://webapi.ctfile.com";

    private static final String API1 = API_URL_PREFIX + "/getfile.php?path=file" +
            "&f={shareKey}&passcode={pwd}&token={token}&r={rand}&ref=";

    private static final String API2 = API_URL_PREFIX + "/get_file_url.php?" +
            "uid={uid}&fid={fid}&folder_id=0&file_chk={file_chk}&mb=0&token={token}&app=0&acheck=0&verifycode=" +
            "&rd={rand}";


    /**
     * 子类重写此构造方法不需要添加额外逻辑
     * 如:
     * <blockquote><pre>
     *  public XxTool(String key, String pwd) {
     *      super(key, pwd);
     *  }
     * </pre></blockquote>
     *
     * @param shareLinkInfo 分享链接信息
     */
    public CtTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }


    @Override
    public Future<String> parse() {
        final String shareKey = shareLinkInfo.getShareKey();
        if (shareKey.indexOf('-') == -1) {
            fail("shareKey格式不正确找不到'-': {}", shareKey);
            return promise.future();
        }
        String[] split = shareKey.split("-");
        String uid = split[0], fid = split[1];
        String token = RandomStringGenerator.generateRandomString();

        HttpRequest<Buffer> bufferHttpRequest1 = clientSession.getAbs(UriTemplate.of(API1))
                .setTemplateParam("shareKey", shareKey)
                .setTemplateParam("pwd", shareLinkInfo.getSharePassword())
                .setTemplateParam("token", token)
                .setTemplateParam("r", Math.random() + "");

        bufferHttpRequest1
                .send().onSuccess(res -> {
                    var resJson = asJson(res);
                    if (resJson.containsKey("file")) {
                        var fileJson = resJson.getJsonObject("file");
                        if (fileJson.containsKey("file_chk")) {
                            var file_chk = fileJson.getString("file_chk");
                            HttpRequest<Buffer> bufferHttpRequest2 = clientSession.getAbs(UriTemplate.of(API2))
                                    .setTemplateParam("uid", uid)
                                    .setTemplateParam("fid", fid)
                                    .setTemplateParam("file_chk", file_chk)
                                    .setTemplateParam("token", token)
                                    .setTemplateParam("rd", Math.random() + "");
                            bufferHttpRequest2
                                    .send().onSuccess(res2 -> {
                                        JsonObject resJson2 = asJson(res2);
                                        if (resJson2.containsKey("downurl")) {
                                            promise.complete(resJson2.getString("downurl"));
                                        } else {
                                            fail("解析失败, 可能分享已失效: json: {} 字段 {} 不存在", resJson2, "downurl");
                                        }
                                    }).onFailure(handleFail(bufferHttpRequest1.queryParams().toString()));
                        } else {
                            fail("解析失败, 可能分享已失效: json: {} 字段 {} 不存在", resJson, "file_chk");
                        }
                    } else {
                        fail("解析失败, 可能分享已失效: json: {} 字段 {} 不存在", resJson, "file");
                    }
                }).onFailure(handleFail(bufferHttpRequest1.queryParams().toString()));
        return promise.future();
    }
}
