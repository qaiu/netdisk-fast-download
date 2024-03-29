package cn.qaiu.lz.web.service;

import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;

/**
 * lz-web
 * <br>Create date 2021/8/27 14:06
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@ProxyGen
public interface UserService extends BaseAsyncService {
    Future<SysUser> login(SysUser user);
}
