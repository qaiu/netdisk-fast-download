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
     * MIME类型
     */
    String fileMIME;

    /**
     * 文件路径
     */
    String filePath;

    /**
     * 创建(上传)时间 yyyy-MM-dd HH:mm:ss格式
     */
    String createTime;

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

    public String getFileMIME() {
        return fileMIME;
    }

    public FileInfo setFileMIME(String fileMIME) {
        this.fileMIME = fileMIME;
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
