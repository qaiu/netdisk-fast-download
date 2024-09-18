package cn.qaiu.lz.web.model;

import cn.qaiu.lz.common.ToJson;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@DataObject
public class StatisticsInfo implements ToJson {
    Integer parserTotal;
    Integer cacheTotal;
    Integer total;


    public StatisticsInfo(JsonObject jsonObject) {
        this.parserTotal = jsonObject.getInteger("parserTotal");
        this.cacheTotal = jsonObject.getInteger("cacheTotal");
        this.total = jsonObject.getInteger("total");
    }
}
