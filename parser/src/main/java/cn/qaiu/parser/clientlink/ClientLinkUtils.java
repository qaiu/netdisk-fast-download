package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.ShareLinkInfo;

import java.util.Map;

/**
 * 客户端下载链接生成工具类
 * 提供便捷的静态方法来生成各种客户端下载链接
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public class ClientLinkUtils {
    
    /**
     * 为 ShareLinkInfo 生成所有类型的客户端下载链接
     * 
     * @param info ShareLinkInfo 对象
     * @return Map<ClientLinkType, String> 格式的客户端链接集合
     */
    public static Map<ClientLinkType, String> generateAllClientLinks(ShareLinkInfo info) {
        return ClientLinkGeneratorFactory.generateAll(info);
    }
    
    /**
     * 生成指定类型的客户端下载链接
     * 
     * @param info ShareLinkInfo 对象
     * @param type 客户端类型
     * @return 生成的客户端链接字符串
     */
    public static String generateClientLink(ShareLinkInfo info, ClientLinkType type) {
        return ClientLinkGeneratorFactory.generate(info, type);
    }
    
    /**
     * 生成 curl 命令
     * 
     * @param info ShareLinkInfo 对象
     * @return curl 命令字符串
     */
    public static String generateCurlCommand(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.CURL);
    }
    
    /**
     * 生成 wget 命令
     * 
     * @param info ShareLinkInfo 对象
     * @return wget 命令字符串
     */
    public static String generateWgetCommand(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.WGET);
    }
    
    /**
     * 生成 aria2 命令
     * 
     * @param info ShareLinkInfo 对象
     * @return aria2 命令字符串
     */
    public static String generateAria2Command(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.ARIA2);
    }
    
    /**
     * 生成迅雷链接
     * 
     * @param info ShareLinkInfo 对象
     * @return 迅雷协议链接
     */
    public static String generateThunderLink(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.THUNDER);
    }
    
    /**
     * 生成 IDM 链接
     * 
     * @param info ShareLinkInfo 对象
     * @return IDM 协议链接
     */
    public static String generateIdmLink(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.IDM);
    }
    
    /**
     * 生成比特彗星链接
     * 
     * @param info ShareLinkInfo 对象
     * @return 比特彗星协议链接
     */
    public static String generateBitCometLink(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.BITCOMET);
    }
    
    /**
     * 生成 Motrix 导入格式
     * 
     * @param info ShareLinkInfo 对象
     * @return Motrix JSON 格式字符串
     */
    public static String generateMotrixFormat(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.MOTRIX);
    }
    
    /**
     * 生成 FDM 导入格式
     * 
     * @param info ShareLinkInfo 对象
     * @return FDM 格式字符串
     */
    public static String generateFdmFormat(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.FDM);
    }
    
    /**
     * 生成 PowerShell 命令
     * 
     * @param info ShareLinkInfo 对象
     * @return PowerShell 命令字符串
     */
    public static String generatePowerShellCommand(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.POWERSHELL);
    }
    
    /**
     * 检查 ShareLinkInfo 是否包含有效的下载元数据
     * 
     * @param info ShareLinkInfo 对象
     * @return true 表示包含有效元数据，false 表示不包含
     */
    public static boolean hasValidDownloadMeta(ShareLinkInfo info) {
        if (info == null || info.getOtherParam() == null) {
            return false;
        }
        
        Object downloadUrl = info.getOtherParam().get("downloadUrl");
        return downloadUrl instanceof String && !((String) downloadUrl).trim().isEmpty();
    }
}
