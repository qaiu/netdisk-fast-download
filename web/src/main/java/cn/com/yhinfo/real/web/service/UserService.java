package cn.com.yhinfo.real.web.service;

import cn.com.yhinfo.core.base.BaseAsyncService;
import cn.com.yhinfo.real.web.model.RealUser;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;

/**
 * sinoreal2-web
 * <br>Create date 2021/8/27 14:06
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@ProxyGen
public interface UserService extends BaseAsyncService {
    Future<String> login(RealUser user);
}
