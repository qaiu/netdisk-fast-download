package cn.com.yhinfo.real.common.model;

/**
 * Mapper for {@link UserInfo}.
 * NOTE: This class has been automatically generated from the {@link UserInfo} original class using Vert.x codegen.
 */
@io.vertx.codegen.annotations.VertxGen
public interface UserInfoRowMapper extends io.vertx.sqlclient.templates.RowMapper<UserInfo> {

  @io.vertx.codegen.annotations.GenIgnore
  UserInfoRowMapper INSTANCE = new UserInfoRowMapper() { };

  @io.vertx.codegen.annotations.GenIgnore
  java.util.stream.Collector<io.vertx.sqlclient.Row, ?, java.util.List<UserInfo>> COLLECTOR = java.util.stream.Collectors.mapping(INSTANCE::map, java.util.stream.Collectors.toList());

  @io.vertx.codegen.annotations.GenIgnore
  default UserInfo map(io.vertx.sqlclient.Row row) {
    UserInfo obj = new UserInfo();
    Object val;
    int idx;
    if ((idx = row.getColumnIndex("permission")) != -1 && (val = row.getString(idx)) != null) {
      obj.setPermission((java.lang.String)val);
    }
    if ((idx = row.getColumnIndex("pwd_crc32")) != -1 && (val = row.getString(idx)) != null) {
      obj.setPwdCrc32((java.lang.String)val);
    }
    if ((idx = row.getColumnIndex("username")) != -1 && (val = row.getString(idx)) != null) {
      obj.setUsername((java.lang.String)val);
    }
    if ((idx = row.getColumnIndex("uuid")) != -1 && (val = row.getString(idx)) != null) {
      obj.setUuid((java.lang.String)val);
    }
    return obj;
  }
}
