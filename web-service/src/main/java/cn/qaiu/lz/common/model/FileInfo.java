package cn.qaiu.lz.common.model;

import io.vertx.core.MultiMap;

public class FileInfo extends BaseModel {

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String download;

    private MultiMap header;
}
