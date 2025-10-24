package cn.qaiu.parser.clientlink;

/**
 * 客户端下载链接生成器接口
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
public interface ClientLinkGenerator {
    
    /**
     * 生成客户端下载链接
     * 
     * @param meta 下载链接元数据
     * @return 生成的客户端下载链接字符串
     */
    String generate(DownloadLinkMeta meta);
    
    /**
     * 获取生成器对应的客户端类型
     * 
     * @return ClientLinkType 枚举值
     */
    ClientLinkType getType();
    
    /**
     * 检查是否支持生成该类型的链接
     * 默认实现：检查元数据是否有有效的URL
     * 
     * @param meta 下载链接元数据
     * @return true 表示支持，false 表示不支持
     */
    default boolean supports(DownloadLinkMeta meta) {
        return meta != null && meta.hasValidUrl();
    }
}
