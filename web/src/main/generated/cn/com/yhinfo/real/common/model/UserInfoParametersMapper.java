package cn.com.yhinfo.real.common.model;

/**
 * Mapper for {@link UserInfo}.
 * NOTE: This class has been automatically generated from the {@link UserInfo} original class using Vert.x codegen.
 */
@io.vertx.codegen.annotations.VertxGen
public interface UserInfoParametersMapper extends io.vertx.sqlclient.templates.TupleMapper<UserInfo> {

  UserInfoParametersMapper INSTANCE = new UserInfoParametersMapper() {};

  default io.vertx.sqlclient.Tuple map(java.util.function.Function<Integer, String> mapping, int size, UserInfo params) {
    java.util.Map<String, Object> args = map(params);
    Object[] array = new Object[size];
    for (int i = 0;i < array.length;i++) {
      String column = mapping.apply(i);
      array[i] = args.get(column);
    }
    return io.vertx.sqlclient.Tuple.wrap(array);
  }

  default java.util.Map<String, Object> map(UserInfo obj) {
    java.util.Map<String, Object> params = new java.util.HashMap<>();
    params.put("permission", obj.getPermission());
    params.put("pwd_crc32", obj.getPwdCrc32());
    params.put("username", obj.getUsername());
    params.put("uuid", obj.getUuid());
    return params;
  }
}
