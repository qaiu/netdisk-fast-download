package cn.qaiu.entity;

public class ShareLinkInfo {

    private String shareKey;      // 分享键
    private String type;          // 分享类型
    private String sharePassword; // 分享密码（如果存在）
    private String shareUrl;      // 原始分享链接
    private String standardUrl;   // 规范化的标准链接

    private ShareLinkInfo(Builder builder) {
        this.shareKey = builder.shareKey;
        this.type = builder.type;
        this.sharePassword = builder.sharePassword;
        this.shareUrl = builder.shareUrl;
        this.standardUrl = builder.standardUrl;
    }

    // Getter和Setter方法

    public String getShareKey() {
        return shareKey;
    }

    public void setShareKey(String shareKey) {
        this.shareKey = shareKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSharePassword() {
        return sharePassword;
    }

    public void setSharePassword(String sharePassword) {
        this.sharePassword = sharePassword;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }

    public String getStandardUrl() {
        return standardUrl;
    }

    public void setStandardUrl(String standardUrl) {
        this.standardUrl = standardUrl;
    }

    // 静态方法创建建造者对象
    public static ShareLinkInfo.Builder newBuilder() {
        return new ShareLinkInfo.Builder();
    }

    // 建造者类
    public static class Builder {
        private String shareKey;      // 分享键
        private String type;          // 分享类型 (网盘模板枚举的小写)
        private String sharePassword = ""; // 分享密码（如果存在）
        private String shareUrl;      // 原始分享链接
        private String standardUrl;   // 规范化的标准链接

        public Builder shareKey(String shareKey) {
            this.shareKey = shareKey;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder sharePassword(String sharePassword) {
            this.sharePassword = sharePassword;
            return this;
        }

        public Builder shareUrl(String shareUrl) {
            this.shareUrl = shareUrl;
            return this;
        }

        public Builder standardUrl(String standardUrl) {
            this.standardUrl = standardUrl;
            return this;
        }

        public ShareLinkInfo build() {
            return new ShareLinkInfo(this);
        }
    }

    @Override
    public String toString() {
        return "ShareLinkInfo{" +
                "shareKey='" + shareKey + '\'' +
                ", type='" + type + '\'' +
                ", sharePassword='" + sharePassword + '\'' +
                ", shareUrl='" + shareUrl + '\'' +
                ", standardUrl='" + standardUrl + '\'' +
                '}';
    }
}
