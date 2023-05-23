package cn.qaiu.lz.common.util;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * 移动云空间解析
 */
public class EcTool {
    public static String FULL_URL_PREFIX = "https://www.ecpan.cn/drive/fileextoverrid.do?chainUrlTemplate=https:%2F%2Fwww.ecpan.cn%2Fweb%2F%23%2FyunpanProxy%3Fpath%3D%252F%2523%252Fdrive%252Foutside&parentId=-1&data=";


    public static String parse(String dataKey) throws Exception {
        Vertx vertx = Vertx.vertx();
        WebClient client = WebClient.create(vertx);
        try {

        client.getAbs(FULL_URL_PREFIX+dataKey).send().onSuccess(
                res -> {
                        System.out.println(res.bodyAsString());
                }
        ).onFailure(t -> {
            throw new RuntimeException("解析失败");
        });

        } catch (RuntimeException e) {
            throw new Exception(e);
        }
        return "";
    }

    public static void main(String[] args) throws Exception {
        parse("81027a5c99af5b11ca004966c945cce6W9Bf2");
        System.out.println("222222");
    }
}
