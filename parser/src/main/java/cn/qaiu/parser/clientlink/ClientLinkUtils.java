package cn.qaiu.parser.clientlink;

import cn.qaiu.entity.ShareLinkInfo;

import java.util.Map;

/**
 * 客户端下载链接生成工具类
 * 提供便捷的静态方法来生成各种客户端下载链接
 * <p>
 * 支持的客户端类型：
 * <ul>
 *     <li>CURL - cURL 命令，支持 Cookie</li>
 *     <li>ARIA2 - Aria2 命令，支持 Cookie</li>
 *     <li>THUNDER - 迅雷协议，不支持 Cookie</li>
 * </ul>
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
     * 生成 curl 命令（支持 Cookie）
     * 
     * @param info ShareLinkInfo 对象
     * @return curl 命令字符串
     */
    public static String generateCurlCommand(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.CURL);
    }
    
    /**
     * 生成 aria2 命令（支持 Cookie）
     * 
     * @param info ShareLinkInfo 对象
     * @return aria2 命令字符串
     */
    public static String generateAria2Command(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.ARIA2);
    }
    
    /**
     * 生成迅雷链接（不支持 Cookie）
     * 
     * @param info ShareLinkInfo 对象
     * @return 迅雷协议链接
     */
    public static String generateThunderLink(ShareLinkInfo info) {
        return generateClientLink(info, ClientLinkType.THUNDER);
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
