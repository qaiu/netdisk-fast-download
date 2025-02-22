package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.UriTemplate;

/**
 * <a href="https://passport2.chaoxing.com">超星云盘</a>
 */
public class PcxTool extends PanBase {

    public PcxTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        client.getAbs(shareLinkInfo.getShareUrl())
                .send().onSuccess(res -> {
                    // 'download':  'https://d0.ananas.chaoxing.com/download/de08dcf546e4dd88a17bead86ff6338d?at_=1740211698795&ak_=d62a3acbd5ce43e1e8565b67990691e4&ad_=8c4ef22e980ee0dd9532ec3757ab19f8&fn=33.c'
                    String body = res.bodyAsString();
                    // 获取download
                    String str = "var fileinfo = {";
                    String fileInfo = res.bodyAsString().substring(res.bodyAsString().indexOf(str) + str.length() - 1
                            , res.bodyAsString().indexOf("};") + 1);
                    fileInfo = fileInfo.replace("'", "\"");
                    JsonObject jsonObject = new JsonObject(fileInfo);
                    String download = jsonObject.getString("download");
                    if (download.contains("fn=")) {
                        complete(download);
                    } else {
                        fail("获取下载链接失败: 不支持的文件类型: {}", jsonObject.getString("suffix"));
                    }
                }).onFailure(handleFail(shareLinkInfo.getShareUrl()));
        return promise.future();
    }


//    public static void main(String[] args) {
//        String s = new PcxTool(ShareLinkInfo.newBuilder().shareUrl("https://pan-yz.cldisk.com/external/m/file/953658049102462976")
//                .shareKey("953658049102462976")
//                .build()).parseSync();
//        System.out.println(s);
//    }
}
