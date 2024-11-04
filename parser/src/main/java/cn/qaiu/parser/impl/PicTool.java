package cn.qaiu.parser.impl;

import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * <a href="https://www.icloud.com.cn/">iCloud云盘(pic)</a>
 */
public class PicTool extends PanBase {


    private static final String api = "https://ckdatabasews.icloud.com.cn/database/1/com.apple.cloudkit/production/public/records/resolve";

    public PicTool(ShareLinkInfo shareLinkInfo) {
        super(shareLinkInfo);
    }

    public Future<String> parse() {
        // {"shortGUIDs":[{"value":"xxx"}]}
        JsonObject jsonObject =
                new JsonObject("{\"shortGUIDs\":[{\"value\":\"%s\"}]}".formatted(shareLinkInfo.getShareKey()));

        client.postAbs(api).sendJsonObject(jsonObject).onSuccess(res -> {
            // results->rootRecord->fields->fileContent->value->downloadURL // ${f}->fileName
            // fileName: results->share->fields->cloudkit.title->value + "." + results->rootRecord->fields->extension->value
            JsonObject json = asJson(res);
            try {
                JsonObject result = json.getJsonArray("results").getJsonObject(0);
                JsonObject fileInfo = result
                        .getJsonObject("rootRecord")
                        .getJsonObject("fields");

                String downURL = fileInfo
                        .getJsonObject("fileContent")
                        .getJsonObject("value")
                        .getString("downloadURL");

                String extension = fileInfo
                        .getJsonObject("extension")
                        .getString("value");

                String fileTitle = result
                        .getJsonObject("share")
                        .getJsonObject("fields")
                        .getJsonObject("cloudkit.title")
                        .getString("value");
                complete(downURL.replace("${f}", fileTitle + "." + extension));
            } catch (Exception e) {
                fail(e, "json解析失败");
            }

        }).onFailure(handleFail());

        return promise.future();
    }
}
