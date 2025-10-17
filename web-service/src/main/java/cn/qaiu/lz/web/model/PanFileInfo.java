package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Table;
import cn.qaiu.entity.FileInfo;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.format.SnakeCase;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.templates.annotations.RowMapped;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/8/4 12:38
 */
@Table(keyFields = "share_key")
@DataObject
@RowMapped(formatter = SnakeCase.class)
@NoArgsConstructor
@Data
public class PanFileInfo {

    String shareKey;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件ID
     */
    private String fileId;

    private String fileIcon;

    /**
     * 文件大小(byte)
     */
    private Long size;


    private String sizeStr;

    /**
     * 类型
     */
    private String fileType;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 创建(上传)时间 yyyy-MM-dd HH:mm:ss格式
     */
    private String createTime;

    /**
     * 上次修改时间
     */
    private String updateTime;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 文件描述
     */
    private String description;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 网盘标识
     */
    private String panType;

    /**
     * nfd下载链接(可能获取不到)
     * note: 不是下载直链
     */
    private String parserUrl;

    //预览地址
    private String previewUrl;

    // 文件hash默认类型为md5
    private String hash;

    public PanFileInfo(JsonObject jsonObject) {
        this.shareKey = jsonObject.getString("shareKey");
        this.fileName = jsonObject.getString("fileName");
        this.fileId = jsonObject.getString("fileId");
        this.fileIcon = jsonObject.getString("fileIcon");
        this.size = jsonObject.getLong("size");
        this.sizeStr = jsonObject.getString("sizeStr");
        this.fileType = jsonObject.getString("fileType");
        this.filePath = jsonObject.getString("filePath");
        this.createTime = jsonObject.getString("createTime");
        this.updateTime = jsonObject.getString("updateTime");
        this.createBy = jsonObject.getString("createBy");
        this.description = jsonObject.getString("description");
        this.downloadCount = jsonObject.getInteger("downloadCount");
        this.panType = jsonObject.getString("panType");
        this.parserUrl = jsonObject.getString("parserUrl");
        this.previewUrl = jsonObject.getString("previewUrl");
        this.hash = jsonObject.getString("hash");
    }
    public static PanFileInfo fromFileInfo(FileInfo info) {
        PanFileInfo panFileInfo = new PanFileInfo();
        if (info == null) {
            return panFileInfo;
        }

        // 拷贝 FileInfo 的字段
        panFileInfo.setFileName(info.getFileName());
        panFileInfo.setFileId(info.getFileId());
        panFileInfo.setFileIcon(info.getFileIcon());
        panFileInfo.setSize(info.getSize());
        panFileInfo.setSizeStr(info.getSizeStr());
        panFileInfo.setFileType(info.getFileType());
        panFileInfo.setFilePath(info.getFilePath());
        panFileInfo.setCreateTime(info.getCreateTime());
        panFileInfo.setUpdateTime(info.getUpdateTime());
        panFileInfo.setCreateBy(info.getCreateBy());
        panFileInfo.setDescription(info.getDescription());
        panFileInfo.setDownloadCount(info.getDownloadCount());
        panFileInfo.setPanType(info.getPanType());
        panFileInfo.setParserUrl(info.getParserUrl());
        panFileInfo.setPreviewUrl(info.getPreviewUrl());
        panFileInfo.setHash(info.getHash());
        return panFileInfo;
    }

    public FileInfo toFileInfo() {
        FileInfo fileInfo = new FileInfo();

        fileInfo.setFileName(this.getFileName());
        fileInfo.setFileId(this.getFileId());
        fileInfo.setFileIcon(this.getFileIcon());
        fileInfo.setSize(this.getSize());
        fileInfo.setSizeStr(this.getSizeStr());
        fileInfo.setFileType(this.getFileType());
        fileInfo.setFilePath(this.getFilePath());
        fileInfo.setCreateTime(this.getCreateTime());
        fileInfo.setUpdateTime(this.getUpdateTime());
        fileInfo.setCreateBy(this.getCreateBy());
        fileInfo.setDescription(this.getDescription());
        fileInfo.setDownloadCount(this.getDownloadCount());
        fileInfo.setPanType(this.getPanType());
        fileInfo.setParserUrl(this.getParserUrl());
        fileInfo.setPreviewUrl(this.getPreviewUrl());
        fileInfo.setHash(this.getHash());
        return fileInfo;
    }
}
