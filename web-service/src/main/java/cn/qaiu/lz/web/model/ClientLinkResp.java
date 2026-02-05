package cn.qaiu.lz.web.model;

import cn.qaiu.parser.clientlink.ClientLinkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 客户端下载链接响应模型
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2025/01/21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientLinkResp {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 直链URL
     */
    private String directLink;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小
     */
    private Long fileSize;
    
    /**
     * 所有客户端下载链接
     */
    private Map<ClientLinkType, String> clientLinks;
    
    /**
     * 支持的客户端类型列表
     */
    private Map<String, String> supportedClients;
    
    /**
     * 解析信息
     */
    private String parserInfo;
    
    /**
     * 网盘类型代码
     */
    private String panType;
    
    /**
     * 是否必须使用客户端下载（直链需要特殊头部，浏览器无法直接下载）
     * 适用于：UC、QK、PCX、COW等
     */
    private boolean requiresClient;
    
    /**
     * 认证需求级别：
     * - "none": 不需要认证
     * - "required": 必须认证（UC、QK）
     * - "optional": 可选认证，大文件需要（FJ、IZ）
     */
    private String authRequirement;
    
    /**
     * 认证提示信息
     */
    private String authHint;
}
