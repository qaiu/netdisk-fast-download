package cn.qaiu.lz.web.model;

import cn.qaiu.entity.ShareLinkInfo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkInfoResp {
    // 解析链接
    private String downLink;
    private String apiLink;
    private Integer cacheHitTotal;
    private Integer parserTotal;
    private Integer sumTotal;
    private ShareLinkInfo shareLinkInfo;
}
