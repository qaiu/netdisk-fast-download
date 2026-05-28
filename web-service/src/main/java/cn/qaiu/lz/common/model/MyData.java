package cn.qaiu.lz.common.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * <br>Create date 2021/7/22 3:34
 */
@DataObject
@Data
@NoArgsConstructor
public class MyData implements Serializable {
    public static final long serialVersionUID = 1L;

    private String id;

    private String maxSize;


    public MyData(JsonObject jsonObject) {
        // TODO
    }
}
