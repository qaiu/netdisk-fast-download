package cn.qaiu.parser.clientlink;

/**
 * 客户端下载工具类型枚举
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public enum ClientLinkType {
    ARIA2("aria2", "Aria2"),
    MOTRIX("motrix", "Motrix"),
    BITCOMET("bitcomet", "比特彗星"),
    THUNDER("thunder", "迅雷"),
    WGET("wget", "wget 命令"),
    CURL("curl", "cURL 命令"),
    IDM("idm", "IDM"),
    FDM("fdm", "Free Download Manager"),
    POWERSHELL("powershell", "PowerShell");
    
    private final String code;
    private final String displayName;
    
    ClientLinkType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
