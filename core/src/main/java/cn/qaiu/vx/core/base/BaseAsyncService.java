package cn.qaiu.vx.core.base;

import cn.qaiu.vx.core.util.CastUtil;

import java.util.Arrays;

/**
 * 反射获取父接口类名辅助类, 异步服务需要实现此接口
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public interface BaseAsyncService {

    /**
     * 获取异步服务接口地址
     * @see BaseAsyncService#getAsyncInterfaceClass
     * @return 服务接口类名
     * @throws ClassNotFoundException 接口不存在
     */
    default String getAddress() throws ClassNotFoundException {
        return getAsyncInterfaceClass().getName();
    }

    /**
     * 获取异步服务接口地址
     * @return 服务接口类对象
     * @throws ClassNotFoundException 接口不存在
     */
    default Class<Object> getAsyncInterfaceClass() throws ClassNotFoundException {
        // 获取实现接口 作为地址注册到EventBus
        Class<Object> clazz = CastUtil.cast(Arrays.stream(this.getClass().getInterfaces()).filter(
                clz-> Arrays.asList(clz.getInterfaces()).contains(BaseAsyncService.class)
        ).findFirst().orElse(null));
        if (clazz == null) {
            throw new ClassNotFoundException("No interface found: \""+this.getClass().getName()+"\" need to implement interface");
        }
        return clazz;
    }
}
