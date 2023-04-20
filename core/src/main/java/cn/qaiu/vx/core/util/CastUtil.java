package cn.qaiu.vx.core.util;

/**
 * 转换为任意类型 旨在消除泛型转换时的异常
 */
public interface CastUtil {

    /**
     * 泛型转换
     * @param object 要转换的object
     * @param <T> T
     * @return T
     */
    @SuppressWarnings("unchecked")
    static <T> T cast(Object object) {
        return (T) object;
    }
}
