package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://onedrive.live.com/">onedrive分享(od)</a>
 */
public class PodTool extends PanBase {

    /*
     * https://1drv.ms/w/s!Alg0feQmCv2rnRFd60DQOmMa-Oh_?e=buaRtp --302->
     * https://api.onedrive.com/v1.0/drives/abfd0a26e47d3458/items/ABFD0A26E47D3458!3729?authkey=!AF3rQNA6Yxr46H8
     * https://onedrive.live.com/redir?resid=(?<cid>)!(?<cid2>)&authkey=(?<authkey>)&e=hV98W1
     * cid: abfd0a26e47d3458, cid2: ABFD0A26E47D3458!3729 authkey: !AF3rQNA6Yxr46H8
     * -> @content.downloadUrl
     */

    private static final String api = "https://api.onedrive.com/v1.0/drives/{cid}/items/{cid20}?authkey={authkey}";
    private static final Pattern redirectUrlRegex =
            Pattern.compile("=(?<cid>.+)!(?<cid2>.+)&authkey=(?<authkey>.+)&e=(?<e>.+)");

    public PodTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        clientNoRedirects.getAbs(shareLinkInfo.getShareUrl()).send().onSuccess(r0 -> {
            String location = r0.getHeader("Location");
            Matcher matcher = redirectUrlRegex.matcher(location);
            if (matcher.find()) {
                var cid= matcher.group("cid");
                var cid2= matcher.group("cid2");
                var authkey= matcher.group("authkey");
                client.getAbs(UriTemplate.of(api))
                        .setTemplateParam("cid", cid)
                        .setTemplateParam("cid20", cid + "!" + cid2)
                        .setTemplateParam("authkey", authkey).send()
                        .onSuccess(res -> {
                            JsonObject jsonObject = asJson(res);
                            // System.out.println(jsonObject);
                            complete(jsonObject.getString("@content.downloadUrl"));
                        })
                        .onFailure(handleFail());
            }
        }).onFailure(handleFail());

        return promise.future();
    }

    //https://onedrive.live.com/redir?resid=ABFD0A26E47D3458!4699&e=OggA4s&migratedtospo=true&redeem=aHR0cHM6Ly8xZHJ2Lm1zL3UvcyFBbGcwZmVRbUN2MnJwRnZ1NDQ0aGc1eVZxRGNLP2U9T2dnQTRz
//    public static void main(String[] args) {
//        Matcher matcher = redirectUrlRegex.matcher("https://onedrive.live.com/redir?resid=ABFD0A26E47D3458!4698" +
//                "&authkey=!ACpvXghP5xhG_cg&e=hV98W1");
//        if (matcher.find()) {
//            System.out.println(matcher.group("cid"));
//            System.out.println(matcher.group("cid2"));
//            System.out.println(matcher.group("authkey"));
//        }

//    }
}
