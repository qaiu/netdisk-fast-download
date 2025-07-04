package cn.qaiu.entity;

import java.util.Map;

public class FileInfo {

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

    /**
     * 扩展参数
     */
    private Map<String, Object> extParameters;

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

    public String getFileIcon() {
        return fileIcon;
    }

    public FileInfo setFileIcon(String fileIcon) {
        this.fileIcon = fileIcon;
        return this;
    }

    public Long getSize() {
        return size;
    }

    public FileInfo setSize(Long size) {
        this.size = size;
        return this;
    }

    public String getSizeStr() {
        return sizeStr;
    }

    public FileInfo setSizeStr(String sizeStr) {
        this.sizeStr = sizeStr;
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

    public String getPanType() {
        return panType;
    }

    public FileInfo setPanType(String panType) {
        this.panType = panType;
        return this;
    }

    public String getParserUrl() {
        return parserUrl;
    }

    public FileInfo setParserUrl(String parserUrl) {
        this.parserUrl = parserUrl;
        return this;
    }
    public String getPreviewUrl() {
        return previewUrl;
    }
    public FileInfo setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
        return this;
    }

    public Map<String, Object> getExtParameters() {
        return extParameters;
    }

    public FileInfo setExtParameters(Map<String, Object> extParameters) {
        this.extParameters = extParameters;
        return this;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "fileName='" + fileName + '\'' +
                ", fileId='" + fileId + '\'' +
                ", size=" + size +
                ", fileType='" + fileType + '\'' +
                ", filePath='" + filePath + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", createBy='" + createBy + '\'' +
                ", description='" + description + '\'' +
                ", downloadCount=" + downloadCount +
                ", extParameters=" + extParameters +
                '}';
    }
}
