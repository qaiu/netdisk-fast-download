package cn.com.yhinfo.real.common.model;

/**
 * Mapper for {@link MyData}.
 * NOTE: This class has been automatically generated from the {@link MyData} original class using Vert.x codegen.
 */
@io.vertx.codegen.annotations.VertxGen
public interface MyDataParametersMapper extends io.vertx.sqlclient.templates.TupleMapper<MyData> {

  MyDataParametersMapper INSTANCE = new MyDataParametersMapper() {};

  default io.vertx.sqlclient.Tuple map(java.util.function.Function<Integer, String> mapping, int size, MyData params) {
    java.util.Map<String, Object> args = map(params);
    Object[] array = new Object[size];
    for (int i = 0;i < array.length;i++) {
      String column = mapping.apply(i);
      array[i] = args.get(column);
    }
    return io.vertx.sqlclient.Tuple.wrap(array);
  }

  default java.util.Map<String, Object> map(MyData obj) {
    java.util.Map<String, Object> params = new java.util.HashMap<>();
    params.put("id", obj.getId());
    params.put("max_size", obj.getMaxSize());
    return params;
  }
}
