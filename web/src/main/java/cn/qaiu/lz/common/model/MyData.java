package cn.qaiu.lz.common.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.templates.annotations.ParametersMapped;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * <br>Create date 2021/7/22 3:34
 */
@DataObject
@RowMapped(formatter = SnakeCase.class)
@ParametersMapped(formatter = SnakeCase.class)
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
