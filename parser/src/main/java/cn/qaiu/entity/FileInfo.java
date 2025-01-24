package cn.qaiu.entity;

import java.util.Map;

public class FileInfo {

    /**
     * 文件名
     */
    String fileName;

    /**
     * 文件ID
     */
    String fileId;

    /**
     * 文件大小(byte)
     */
    Long size;

    /**
     * 类型
     */
    String fileType;

    /**
     * 文件路径
     */
    String filePath;

    /**
     * 创建(上传)时间 yyyy-MM-dd HH:mm:ss格式
     */
    String createTime;

    /**
     * 上次修改时间
     */
    String updateTime;

    /**
     * 创建者
     */
    String createBy;

    /**
     * 文件描述
     */
    String description;

    /**
     * 下载次数
     */
    Integer downloadCount;

    /**
     * 扩展参数
     */
    Map<String, Object> extParameters;

    public String getFileName() {
        return fileName;
    }

    public FileInfo setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public String getFileId() {
        return fileId;
    }

    public FileInfo setFileId(String fileId) {
        this.fileId = fileId;
        return this;
    }

    public Long getSize() {
        return size;
    }

    public FileInfo setSize(Long size) {
        this.size = size;
        return this;
    }

    public String getFileType() {
        return fileType;
    }

    public FileInfo setFileType(String fileType) {
        this.fileType = fileType;
        return this;
    }

    public String getFilePath() {
        return filePath;
    }

    public FileInfo setFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public String getCreateTime() {
        return createTime;
    }

    public FileInfo setCreateTime(String createTime) {
        this.createTime = createTime;
        return this;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public FileInfo setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
        return this;
    }

    public String getCreateBy() {
        return createBy;
    }

    public FileInfo setCreateBy(String createBy) {
        this.createBy = createBy;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FileInfo setDescription(String description) {
        this.description = description;
        return this;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public FileInfo setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
        return this;
    }

    public Map<String, Object> getExtParameters() {
        return extParameters;
    }

    public FileInfo setExtParameters(Map<String, Object> extParameters) {
        this.extParameters = extParameters;
        return this;
    }
}
