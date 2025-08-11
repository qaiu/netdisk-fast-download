package cn.qaiu.lz.web.service;

import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.vx.core.base.BaseAsyncService;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * 用户服务接口
 * <br>Create date 2021/8/27 14:06
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@ProxyGen
public interface UserService extends BaseAsyncService {
    /**
     * 用户登录
     * @param user 包含用户名和密码的用户对象
     * @return 登录成功返回用户信息和token，失败返回错误信息
     */
    Future<JsonObject> login(SysUser user);

    /**
     * 根据用户名获取用户信息
     * @param username 用户名
     * @return 用户信息
     */
    Future<SysUser> getUserByUsername(String username);

    /**
     * 创建新用户
     * @param user 用户信息
     * @return 创建成功返回用户信息，失败返回错误信息
     */
    Future<SysUser> createUser(SysUser user);

    /**
     * 更新用户信息
     * @param user 用户信息
     * @return 更新成功返回用户信息，失败返回错误信息
     */
    Future<SysUser> updateUser(SysUser user);

    /**
     * 验证token
     * @param token JWT token
     * @return 验证成功返回用户信息，失败返回错误信息
     */
    Future<JsonObject> validateToken(String token);
}
