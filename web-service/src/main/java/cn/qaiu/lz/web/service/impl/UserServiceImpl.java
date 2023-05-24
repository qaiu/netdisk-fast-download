package cn.qaiu.lz.web.service.impl;

import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.vx.core.annotaions.Service;
import io.vertx.core.Future;

/**
 * lz-web
 * <br>Create date 2021/8/27 14:09
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public Future<String> login(SysUser user) {

        return Future.succeededFuture("111");
    }
}
