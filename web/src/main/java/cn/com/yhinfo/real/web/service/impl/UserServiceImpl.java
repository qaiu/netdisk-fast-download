package cn.com.yhinfo.real.web.service.impl;

import cn.com.yhinfo.core.annotaions.Service;
import cn.com.yhinfo.real.web.model.RealUser;
import cn.com.yhinfo.real.web.service.UserService;
import io.vertx.core.Future;

/**
 * sinoreal2-web
 * <br>Create date 2021/8/27 14:09
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public Future<String> login(RealUser user) {

        return Future.succeededFuture("111");
    }
}
