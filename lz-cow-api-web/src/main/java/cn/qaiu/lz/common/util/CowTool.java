package cn.qaiu.lz.common.util;

import cn.qaiu.vx.core.util.CastUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Map;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2023/4/21 21:19
 */
@Slf4j
public class CowTool {
    /*
        First request:
         {
           "code": "0000",
           "message": "success",
           "data": {
             "guid": "e4f41b51-b5da-4f60-9312-37aa10c0aad7",
             "firstFile": {
               "id": "23861191276513345",
             }
           }
         }

         Then request:
         {
            "code": "0000",
            "message": "success",
            "tn": "TN:DE0E092E8A464521983780FBA21D0CD3",
            "data": {
              "downloadUrl": "https://download.cowcs.com..."
            }
          }
    */
    public static String parse(String fullUrl) throws IOException {
        var uniqueUrl = fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
        var baseUrl = "https://cowtransfer.com/core/api/transfer/share";
        var result = Jsoup
                .connect(baseUrl + "?uniqueUrl=" + uniqueUrl).ignoreContentType(true)
                .get()
                .text();
        var objectMapper = new ObjectMapper();
        Map<String, Object> map = objectMapper.readValue(result, new TypeReference<>() {
        });

        if ("success".equals(map.get("message")) && map.containsKey("data")) {
            Map<String, Object> data = CastUtil.cast(map.get("data"));
            var guid = data.get("guid").toString();
            Map<String, Object> firstFile = CastUtil.cast(data.get("firstFile"));
            var fileId = firstFile.get("id").toString();
            var result2 = Jsoup
                    .connect(baseUrl + "/download?transferGuid=" + guid + "&fileId=" + fileId)
                    .ignoreContentType(true)
                    .get()
                    .text();
            Map<String, Object> map2 = objectMapper.readValue(result2, new TypeReference<>() {});

            if ("success".equals(map2.get("message")) && map2.containsKey("data")) {
                Map<String, Object> data2 = CastUtil.cast(map2.get("data"));
                var downloadUrl = data2.get("downloadUrl").toString();
                if (StringUtils.isNotEmpty(downloadUrl)) {
                    log.info("cow parse success: {}", downloadUrl);
                    return downloadUrl;
                }
            }
        }
        log.info("Cow parse field------------->end");
        return null;
    }


}
