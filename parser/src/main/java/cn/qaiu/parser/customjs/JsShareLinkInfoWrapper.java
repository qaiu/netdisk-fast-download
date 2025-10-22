package cn.qaiu.parser.customjs;

import cn.qaiu.entity.ShareLinkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * ShareLinkInfo的JavaScript包装器
 * 为JavaScript提供ShareLinkInfo对象的访问接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/10/17
 */
public class JsShareLinkInfoWrapper {
    
    private static final Logger log = LoggerFactory.getLogger(JsShareLinkInfoWrapper.class);
    
    private final ShareLinkInfo shareLinkInfo;
    
    public JsShareLinkInfoWrapper(ShareLinkInfo shareLinkInfo) {
        this.shareLinkInfo = shareLinkInfo;
    }
    
    /**
     * 获取分享URL
     * @return 分享URL
     */
    public String getShareUrl() {
        return shareLinkInfo.getShareUrl();
    }
    
    /**
     * 获取分享Key
     * @return 分享Key
     */
    public String getShareKey() {
        return shareLinkInfo.getShareKey();
    }
    
    /**
     * 获取分享密码
     * @return 分享密码
     */
    public String getSharePassword() {
        return shareLinkInfo.getSharePassword();
    }
    
    /**
     * 获取网盘类型
     * @return 网盘类型
     */
    public String getType() {
        return shareLinkInfo.getType();
    }
    
    /**
     * 获取网盘名称
     * @return 网盘名称
     */
    public String getPanName() {
        return shareLinkInfo.getPanName();
    }
    
    /**
     * 获取其他参数
     * @param key 参数键
     * @return 参数值
     */
    public Object getOtherParam(String key) {
        if (key == null) {
            return null;
        }
        return shareLinkInfo.getOtherParam().get(key);
    }
    
    /**
     * 获取所有其他参数
     * @return 参数Map
     */
    public Map<String, Object> getAllOtherParams() {
        return shareLinkInfo.getOtherParam();
    }
    
    /**
     * 检查是否包含指定参数
     * @param key 参数键
     * @return true表示包含，false表示不包含
     */
    public boolean hasOtherParam(String key) {
        if (key == null) {
            return false;
        }
        return shareLinkInfo.getOtherParam().containsKey(key);
    }
    
    /**
     * 获取其他参数的字符串值
     * @param key 参数键
     * @return 参数值（字符串形式）
     */
    public String getOtherParamAsString(String key) {
        Object value = getOtherParam(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 获取其他参数的整数值
     * @param key 参数键
     * @return 参数值（整数形式）
     */
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
     * 获取其他参数的布尔值
     * @param key 参数键
     * @return 参数值（布尔形式）
     */
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
     * 获取原始的ShareLinkInfo对象
     * @return ShareLinkInfo对象
     */
    public ShareLinkInfo getOriginalShareLinkInfo() {
        return shareLinkInfo;
    }
    
    @Override
    public String toString() {
        return "JsShareLinkInfoWrapper{" +
                "shareUrl='" + getShareUrl() + '\'' +
                ", shareKey='" + getShareKey() + '\'' +
                ", sharePassword='" + getSharePassword() + '\'' +
                ", type='" + getType() + '\'' +
                ", panName='" + getPanName() + '\'' +
                '}';
    }
}
