package cn.qaiu.parser.custompy;

import cn.qaiu.entity.ShareLinkInfo;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * ShareLinkInfo的Python包装器
 * 为Python脚本提供ShareLinkInfo对象的访问接口
 *
 * @author QAIU
 */
public class PyShareLinkInfoWrapper {
    
    private static final Logger log = LoggerFactory.getLogger(PyShareLinkInfoWrapper.class);
    
    private final ShareLinkInfo shareLinkInfo;
    
    public PyShareLinkInfoWrapper(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
    }
    
    /**
     * 获取分享URL
     * @return 分享URL
     */
    @HostAccess.Export
    public String getShareUrl() {
        return shareLinkInfo.getShareUrl();
    }
    
    /**
     * Python风格方法名 - 获取分享URL
     */
    @HostAccess.Export
    public String get_share_url() {
        return getShareUrl();
    }
    
    /**
     * 获取分享Key
     * @return 分享Key
     */
    @HostAccess.Export
    public String getShareKey() {
        return shareLinkInfo.getShareKey();
    }
    
    /**
     * Python风格方法名 - 获取分享Key
     */
    @HostAccess.Export
    public String get_share_key() {
        return getShareKey();
    }
    
    /**
     * 获取分享密码
     * @return 分享密码
     */
    @HostAccess.Export
    public String getSharePassword() {
        return shareLinkInfo.getSharePassword();
    }
    
    /**
     * Python风格方法名 - 获取分享密码
     */
    @HostAccess.Export
    public String get_share_password() {
        return getSharePassword();
    }
    
    /**
     * 获取网盘类型
     * @return 网盘类型
     */
    @HostAccess.Export
    public String getType() {
        return shareLinkInfo.getType();
    }
    
    /**
     * Python风格方法名 - 获取网盘类型
     */
    @HostAccess.Export
    public String get_type() {
        return getType();
    }
    
    /**
     * 获取网盘名称
     * @return 网盘名称
     */
    @HostAccess.Export
    public String getPanName() {
        return shareLinkInfo.getPanName();
    }
    
    /**
     * Python风格方法名 - 获取网盘名称
     */
    @HostAccess.Export
    public String get_pan_name() {
        return getPanName();
    }
    
    /**
     * 获取其他参数
     * @param key 参数键
     * @return 参数值
     */
    @HostAccess.Export
    public Object getOtherParam(String key) {
        if (key == null) {
            return null;
        }
        return shareLinkInfo.getOtherParam().get(key);
    }
    
    /**
     * Python风格方法名 - 获取其他参数
     */
    @HostAccess.Export
    public Object get_other_param(String key) {
        return getOtherParam(key);
    }
    
    /**
     * 获取所有其他参数
     * @return 参数Map
     */
    @HostAccess.Export
    public Map<String, Object> getAllOtherParams() {
        return shareLinkInfo.getOtherParam();
    }
    
    /**
     * Python风格方法名 - 获取所有其他参数
     */
    @HostAccess.Export
    public Map<String, Object> get_all_other_params() {
        return getAllOtherParams();
    }
    
    /**
     * 检查是否包含指定参数
     * @param key 参数键
     * @return true表示包含，false表示不包含
     */
    @HostAccess.Export
    public boolean hasOtherParam(String key) {
        if (key == null) {
            return false;
        }
        return shareLinkInfo.getOtherParam().containsKey(key);
    }
    
    /**
     * Python风格方法名 - 检查是否包含指定参数
     */
    @HostAccess.Export
    public boolean has_other_param(String key) {
        return hasOtherParam(key);
    }
    
    /**
     * 获取其他参数的字符串值
     * @param key 参数键
     * @return 参数值（字符串形式）
     */
    @HostAccess.Export
    public String getOtherParamAsString(String key) {
        Object value = getOtherParam(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Python风格方法名 - 获取其他参数的字符串值
     */
    @HostAccess.Export
    public String get_other_param_as_string(String key) {
        return getOtherParamAsString(key);
    }
    
    /**
     * 获取其他参数的整数值
     * @param key 参数键
     * @return 参数值（整数形式）
     */
    @HostAccess.Export
    public Integer getOtherParamAsInteger(String key) {
        Object value = getOtherParam(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("无法将参数 {} 转换为整数: {}", key, value);
                return null;
            }
        }
        return null;
    }
    
    /**
     * Python风格方法名 - 获取其他参数的整数值
     */
    @HostAccess.Export
    public Integer get_other_param_as_integer(String key) {
        return getOtherParamAsInteger(key);
    }
    
    /**
     * 获取其他参数的布尔值
     * @param key 参数键
     * @return 参数值（布尔形式）
     */
    @HostAccess.Export
    public Boolean getOtherParamAsBoolean(String key) {
        Object value = getOtherParam(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
    
    /**
     * Python风格方法名 - 获取其他参数的布尔值
     */
    @HostAccess.Export
    public Boolean get_other_param_as_boolean(String key) {
        return getOtherParamAsBoolean(key);
    }
    
    /**
     * 获取原始的ShareLinkInfo对象
     * @return ShareLinkInfo对象
     */
    public ShareLinkInfo getOriginalShareLinkInfo() {
        return shareLinkInfo;
    }
    
    @Override
    public String toString() {
        return "PyShareLinkInfoWrapper{" +
                "shareUrl='" + getShareUrl() + '\'' +
                ", shareKey='" + getShareKey() + '\'' +
                ", sharePassword='" + getSharePassword() + '\'' +
                ", type='" + getType() + '\'' +
                ", panName='" + getPanName() + '\'' +
                '}';
    }
}
