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
    Integer fail;
    Integer success;
    Integer total;


    public StatisticsInfo(JsonObject jsonObject) {
        this.fail = jsonObject.getInteger("fail");
        this.success = jsonObject.getInteger("success");
        this.total = jsonObject.getInteger("total");
    }
}
