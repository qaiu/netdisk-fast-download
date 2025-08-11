package cn.qaiu.lz.web.service.impl;

import cn.qaiu.db.pool.JDBCPoolInit;
import cn.qaiu.lz.common.util.JwtUtil;
import cn.qaiu.lz.common.util.PasswordUtil;
import cn.qaiu.lz.web.model.SysUser;
import cn.qaiu.lz.web.service.UserService;
import cn.qaiu.vx.core.annotaions.Service;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 用户服务实现类
 * <br>Create date 2021/8/27 14:09
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final JDBCPool jdbcPool = JDBCPoolInit.instance().getPool();

    // 初始化方法，确保管理员用户存在
    public void init() {
        // 检查管理员用户是否存在
        getUserByUsername("admin")
                .onSuccess(user -> {
                    log.info("管理员用户已存在");
                })
                .onFailure(err -> {
                    // 创建管理员用户
                    SysUser admin = new SysUser();
                    admin.setId(UUID.randomUUID().toString());
                    admin.setUsername("admin");
                    admin.setPassword(PasswordUtil.hashPassword("admin123"));
                    admin.setEmail("admin@example.com");
                    admin.setRole("admin");
                    admin.setStatus(1);
                    admin.setCreateTime(LocalDateTime.now());
                    
                    createUser(admin)
                            .onSuccess(result -> log.info("管理员用户创建成功"))
                            .onFailure(error -> log.error("管理员用户创建失败", error));
                });
    }

    // 新增一个工具方法来过滤敏感信息
    private SysUser filterSensitiveInfo(SysUser user) {
        if (user != null) {
            SysUser filtered = new SysUser();
            // 复制除密码外的所有字段
            filtered.setId(user.getId());
            filtered.setUsername(user.getUsername());
            filtered.setEmail(user.getEmail());
            filtered.setPhone(user.getPhone());
            filtered.setAvatar(user.getAvatar());
            filtered.setRole(user.getRole());
            filtered.setStatus(user.getStatus());
            filtered.setCreateTime(user.getCreateTime());
            filtered.setLastLoginTime(user.getLastLoginTime());
            return filtered;
        }
        return null;
    }

    // 将Row转换为SysUser对象
    private SysUser rowToUser(Row row) {
        if (row == null) {
            return null;
        }
        
        SysUser user = new SysUser();
        user.setId(row.getString("id"));
        user.setUsername(row.getString("username"));
        user.setPassword(row.getString("password"));
        user.setEmail(row.getString("email"));
        user.setPhone(row.getString("phone"));
        user.setAvatar(row.getString("avatar"));
        user.setRole(row.getString("role"));
        user.setStatus(row.getInteger("status"));
        
        // 处理日期时间字段
        LocalDateTime createTime = row.getLocalDateTime("create_time");
        if (createTime != null) {
            user.setCreateTime(createTime);
        }
        
        LocalDateTime lastLoginTime = row.getLocalDateTime("last_login_time");
        if (lastLoginTime != null) {
            user.setLastLoginTime(lastLoginTime);
        }
        
        return user;
    }

    @Override
    public Future<JsonObject> login(SysUser user) {
        // 参数校验
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            return Future.succeededFuture(new JsonObject()
                    .put("success", false)
                    .put("message", "用户名和密码不能为空"));
        }

        Promise<JsonObject> promise = Promise.promise();
        
        // 查询用户
        String sql = "SELECT * FROM sys_user WHERE username = ?";
        
        jdbcPool.preparedQuery(sql)
                .execute(Tuple.of(user.getUsername()))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        promise.complete(new JsonObject()
                                .put("success", false)
                                .put("message", "用户不存在"));
                        return;
                    }
                    
                    Row row = rows.iterator().next();
                    SysUser existUser = rowToUser(row);
                    
                    // 验证密码
                    if (!PasswordUtil.checkPassword(user.getPassword(), existUser.getPassword())) {
                        promise.complete(new JsonObject()
                                .put("success", false)
                                .put("message", "密码错误"));
                        return;
                    }
                    
                    // 更新最后登录时间
                    LocalDateTime now = LocalDateTime.now();
                    existUser.setLastLoginTime(now);
                    
                    // 更新数据库中的最后登录时间
                    String updateSql = "UPDATE sys_user SET last_login_time = ? WHERE username = ?";
                    jdbcPool.preparedQuery(updateSql)
                            .execute(Tuple.of(
                                    Timestamp.from(now.atZone(ZoneId.systemDefault()).toInstant()),
                                    existUser.getUsername()
                            ))
                            .onFailure(err -> log.error("更新最后登录时间失败", err));
                    
                    // 生成token
                    String token = JwtUtil.generateToken(existUser);
                    
                    // 返回用户信息和token
                    JsonObject value = JsonObject.mapFrom(existUser);
                    value.remove("password");
                    promise.complete(new JsonObject()
                            .put("success", true)
                            .put("message", "登录成功")
                            .put("token", token)
                            .put("user", value));
                })
                .onFailure(err -> {
                    log.error("登录查询失败", err);
                    promise.complete(new JsonObject()
                            .put("success", false)
                            .put("message", "登录失败: " + err.getMessage()));
                });
        
        return promise.future();
    }

    @Override
    public Future<SysUser> getUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return Future.failedFuture("用户名不能为空");
        }

        Promise<SysUser> promise = Promise.promise();
        
        String sql = "SELECT * FROM sys_user WHERE username = ?";
        
        jdbcPool.preparedQuery(sql)
                .execute(Tuple.of(username))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        promise.fail("用户不存在");
                        return;
                    }
                    
                    Row row = rows.iterator().next();
                    SysUser user = rowToUser(row);
                    promise.complete(filterSensitiveInfo(user));
                })
                .onFailure(err -> {
                    log.error("查询用户失败", err);
                    promise.fail("查询用户失败: " + err.getMessage());
                });
        
        return promise.future();
    }

    @Override
    public Future<SysUser> createUser(SysUser user) {
        // 参数校验
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            return Future.failedFuture("用户名和密码不能为空");
        }

        Promise<SysUser> promise = Promise.promise();
        
        // 先检查用户是否已存在
        String checkSql = "SELECT COUNT(*) as count FROM sys_user WHERE username = ?";
        
        jdbcPool.preparedQuery(checkSql)
                .execute(Tuple.of(user.getUsername()))
                .onSuccess(rows -> {
                    Row row = rows.iterator().next();
                    long count = row.getLong("count");
                    
                    if (count > 0) {
                        promise.fail("用户名已存在");
                        return;
                    }
                    
                    // 设置用户ID和创建时间
                    if (user.getId() == null) {
                        user.setId(UUID.randomUUID().toString());
                    }
                    if (user.getCreateTime() == null) {
                        user.setCreateTime(LocalDateTime.now());
                    }
                    
                    // 设置默认角色和状态
                    if (user.getRole() == null) {
                        user.setRole("user");
                    }
                    if (user.getStatus() == null) {
                        user.setStatus(1);
                    }
                    
                    // 对密码进行加密
                    String plainPassword = user.getPassword();
                    user.setPassword(PasswordUtil.hashPassword(plainPassword));
                    
                    // 插入用户
                    String insertSql = "INSERT INTO sys_user (id, username, password, email, phone, avatar, role, status, create_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    
                    jdbcPool.preparedQuery(insertSql)
                            .execute(Tuple.of(
                                    user.getId(),
                                    user.getUsername(),
                                    user.getPassword(),
                                    user.getEmail(),
                                    user.getPhone(),
                                    user.getAvatar(),
                                    user.getRole(),
                                    user.getStatus(),
                                    Timestamp.from(user.getCreateTime().atZone(ZoneId.systemDefault()).toInstant())
                            ))
                            .onSuccess(result -> {
                                promise.complete(filterSensitiveInfo(user));
                            })
                            .onFailure(err -> {
                                log.error("创建用户失败", err);
                                promise.fail("创建用户失败: " + err.getMessage());
                            });
                })
                .onFailure(err -> {
                    log.error("检查用户是否存在失败", err);
                    promise.fail("创建用户失败: " + err.getMessage());
                });
        
        return promise.future();
    }

    @Override
    public Future<SysUser> updateUser(SysUser user) {
        // 参数校验
        if (user == null || user.getUsername() == null) {
            return Future.failedFuture("用户名不能为空");
        }

        Promise<SysUser> promise = Promise.promise();
        
        // 先检查用户是否存在
        String checkSql = "SELECT * FROM sys_user WHERE username = ?";
        
        jdbcPool.preparedQuery(checkSql)
                .execute(Tuple.of(user.getUsername()))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        promise.fail("用户不存在");
                        return;
                    }
                    
                    Row row = rows.iterator().next();
                    SysUser existUser = rowToUser(row);
                    
                    // 构建更新SQL
                    StringBuilder updateSql = new StringBuilder("UPDATE sys_user SET ");
                    Tuple params = Tuple.tuple();
                    
                    if (user.getEmail() != null) {
                        updateSql.append("email = ?, ");
                        params.addValue(user.getEmail());
                    }
                    
                    if (user.getPhone() != null) {
                        updateSql.append("phone = ?, ");
                        params.addValue(user.getPhone());
                    }
                    
                    if (user.getAvatar() != null) {
                        updateSql.append("avatar = ?, ");
                        params.addValue(user.getAvatar());
                    }
                    
                    if (user.getStatus() != null) {
                        updateSql.append("status = ?, ");
                        params.addValue(user.getStatus());
                    }
                    
                    if (user.getRole() != null) {
                        updateSql.append("role = ?, ");
                        params.addValue(user.getRole());
                    }
                    
                    if (user.getPassword() != null) {
                        updateSql.append("password = ?, ");
                        params.addValue(PasswordUtil.hashPassword(user.getPassword()));
                    }
                    
                    // 移除最后的逗号和空格
                    String sql = updateSql.toString();
                    if (sql.endsWith(", ")) {
                        sql = sql.substring(0, sql.length() - 2);
                    }
                    
                    // 如果没有要更新的字段，直接返回
                    if (params.size() == 0) {
                        promise.complete(filterSensitiveInfo(existUser));
                        return;
                    }
                    
                    // 添加WHERE条件
                    sql += " WHERE username = ?";
                    params.addValue(user.getUsername());
                    
                    // 执行更新
                    jdbcPool.preparedQuery(sql)
                            .execute(params)
                            .onSuccess(result -> {
                                // 重新查询用户信息
                                getUserByUsername(user.getUsername())
                                        .onSuccess(promise::complete)
                                        .onFailure(promise::fail);
                            })
                            .onFailure(err -> {
                                log.error("更新用户失败", err);
                                promise.fail("更新用户失败: " + err.getMessage());
                            });
                })
                .onFailure(err -> {
                    log.error("查询用户失败", err);
                    promise.fail("更新用户失败: " + err.getMessage());
                });
        
        return promise.future();
    }

    @Override
    public Future<JsonObject> validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return Future.succeededFuture(new JsonObject()
                    .put("success", false)
                    .put("message", "Token不能为空"));
        }

        // 验证token
        boolean isValid = JwtUtil.validateToken(token);
        if (!isValid) {
            return Future.succeededFuture(new JsonObject()
                    .put("success", false)
                    .put("message", "Token无效或已过期"));
        }

        // 获取用户信息
        String username = JwtUtil.getUsernameFromToken(token);
        
        Promise<JsonObject> promise = Promise.promise();
        
        getUserByUsername(username)
                .onSuccess(user -> {
                    promise.complete(new JsonObject()
                            .put("success", true)
                            .put("message", "Token有效")
                            .put("user", JsonObject.mapFrom(user)));
                })
                .onFailure(err -> {
                    promise.complete(new JsonObject()
                            .put("success", false)
                            .put("message", "用户不存在"));
                });
        
        return promise.future();
    }
}