package cn.qaiu.parser.clientlink;

/**
 * 客户端下载工具类型枚举
 * <p>
 * 支持的客户端类型：
 * <ul>
 *     <li>CURL - cURL 命令行工具，支持 Cookie</li>
 *     <li>ARIA2 - 多线程下载器，支持 Cookie</li>
 *     <li>THUNDER - 迅雷下载器，不支持 Cookie（使用迅雷协议）</li>
 * </ul>
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public enum ClientLinkType {
    CURL("curl", "cURL 命令", true, "命令行下载工具，支持Cookie"),
    ARIA2("aria2", "Aria2", true, "多线程下载器，支持Cookie"),
    THUNDER("thunder", "迅雷", false, "迅雷下载器，不支持Cookie");
    
    private final String code;
    private final String displayName;
    private final boolean supportsCookie;
    private final String description;
    
    ClientLinkType(String code, String displayName, boolean supportsCookie, String description) {
        this.code = code;
        this.displayName = displayName;
        this.supportsCookie = supportsCookie;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isSupportsCookie() {
        return supportsCookie;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
