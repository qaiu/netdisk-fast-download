package cn.qaiu.parser.impl;

import cn.qaiu.entity.FileInfo;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.IPanTool;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 蓝奏云优享解析器选择器
 * 根据配置的鉴权方式选择不同的解析器：
 * - 如果配置了 username 和 password，则使用 IzToolWithAuth (支持大文件)
 * - 否则使用 IzTool (免登录，仅支持小文件)
 */
public class IzSelectorTool implements IPanTool {
    private final IPanTool selectedTool;

    public IzSelectorTool(ShareLinkInfo shareLinkInfo) {
        if (shareLinkInfo.getOtherParam().containsKey("auths")) {
            MultiMap auths = (MultiMap) shareLinkInfo.getOtherParam().get("auths");
            
            // 检查是否配置了账号密码
            if (auths.contains("username") && auths.contains("password")) {
                String username = auths.get("username");
                String password = auths.get("password");
                if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
                    // 使用 IzToolWithAuth (账密登录，支持大文件)
                    this.selectedTool = new IzToolWithAuth(shareLinkInfo);
                    return;
                }
            }
        }
        
        // 无认证信息或认证信息无效，使用免登录版本（仅支持小文件）
        this.selectedTool = new IzTool(shareLinkInfo);
    }

    @Override
    public Future<String> parse() {
        return selectedTool.parse();
    }

    @Override
    public Future<List<FileInfo>> parseFileList() {
        return selectedTool.parseFileList();
    }

    @Override
    public Future<String> parseById() {
        return selectedTool.parseById();
    }

    @Override
    public ShareLinkInfo getShareLinkInfo() {
        return selectedTool.getShareLinkInfo();
    }

    @Override
    public void close() {
        IPanTool.closeQuietly(selectedTool);
    }
}
