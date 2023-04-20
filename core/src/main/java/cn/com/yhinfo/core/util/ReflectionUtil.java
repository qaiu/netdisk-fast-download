package cn.com.yhinfo.core.util;

import javassist.*;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.reflections.Reflections;
import org.reflections.scanners.*;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.ParseException;
import java.util.*;

/**
 * 基于org.reflection和javassist的反射工具包
 * 通过包扫描实现路由地址的注解映射
 * <br>Create date 2021-05-07 10:26:54
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public final class ReflectionUtil {

    /**
     * 获取反射器
     *
     * @param packageAddress Package address String
     * @return Reflections object
     */
    public static Reflections getReflections(String packageAddress) {
        List<String> packageAddressList;
        if (packageAddress.contains(",")) {
            packageAddressList = Arrays.asList(packageAddress.split(","));
        } else if (packageAddress.contains(";")) {
            packageAddressList = Arrays.asList(packageAddress.split(";"));
        } else {
            packageAddressList = Collections.singletonList(packageAddress);
        }
        return getReflections(packageAddressList);
    }

    /**
     * 获取反射器
     *
     * @param packageAddresses Package address List
     * @return Reflections object
     */
    public static Reflections getReflections(List<String> packageAddresses) {
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        FilterBuilder filterBuilder = new FilterBuilder();
        packageAddresses.forEach(str -> {
            Collection<URL> urls = ClasspathHelper.forPackage(str.trim());
            configurationBuilder.addUrls(urls);
            filterBuilder.includePackage(str.trim());
        });

        // 采坑记录 2021-05-08
        // 发现注解api层 没有继承父类时 这里反射一直有问题(Scanner SubTypesScanner was not configured)
        // 因此这里需要手动配置各种Scanner扫描器 -- https://blog.csdn.net/qq_29499107/article/details/106889781
        configurationBuilder.setScanners(
                new SubTypesScanner(false), //允许getAllTypes获取所有Object的子类, 不设置为false则 getAllTypes 会报错.默认为true.
                new MethodParameterNamesScanner(), //设置方法参数名称 扫描器,否则调用getConstructorParamNames 会报错
                new MethodAnnotationsScanner(), //设置方法注解 扫描器, 否则getConstructorsAnnotatedWith,getMethodsAnnotatedWith 会报错
                new MemberUsageScanner(),  //设置 member 扫描器,否则 getMethodUsage 会报错, 不推荐使用,有可能会报错 Caused by: java.lang.ClassCastException: javassist.bytecode.InterfaceMethodrefInfo cannot be cast to javassist.bytecode.MethodrefInfo
                new TypeAnnotationsScanner() //设置类注解 扫描器 ,否则 getTypesAnnotatedWith 会报错
        );

        configurationBuilder.filterInputsBy(filterBuilder);
        return new Reflections(configurationBuilder);
    }

    /**
     * 获取指定类指定方法的参数名和类型列表(忽略重载方法)
     *
     * @param method 方法名(不考虑重载)
     * @return 参数名类型map
     * @apiNote ..
     */
    public static Map<String, Pair<Annotation[], CtClass>> getMethodParameter(Method method) {
        Map<String, Pair<Annotation[], CtClass>> paramMap = new LinkedHashMap<>();
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get(method.getDeclaringClass().getName());
            CtMethod cm = ctClass.getDeclaredMethod(method.getName());
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            MethodInfo methodInfo = cm.getMethodInfo();
            CtClass[] parameterTypes = cm.getParameterTypes();
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
            LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);

            boolean flag = true;
            boolean flag2 = cm.getModifiers() - 1 != AccessFlag.STATIC;
            for (int j = 0, k = 0; j < parameterTypes.length + k; j++) {
                // 注意这里 只能从tableLength处获取 目前还没发现问题
                String name = attr.variableName(j);
                if (!"this".equals(name) && flag && flag2) {
                    k++;
                    continue;
                }
                flag = false;
                paramMap.put(attr.variableName(j + (flag2 ? 1 : 0)), Pair.of(parameterAnnotations[j - k], parameterTypes[j - k]));
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return paramMap;
    }

    /**
     * 类型转换: 字符串转对应类型
     *
     * @param ctClass 目标类: javassist的ctClass类对象
     * @param value   字符串值
     * @param fmt     日期格式(如果value是日期的话这个字段将有用,否则置为null或空字符串)
     * @return 基本类型或目标对象
     */
    public static Object conversion(CtClass ctClass, String value, String fmt) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        if (StringUtils.isEmpty(fmt)) {
            fmt = "yyyy-MM-dd";
        }
        String name = ctClass.getName();
        if (ctClass.isArray()) {
            name = ctClass.getName().substring(0, ctClass.getName().length() - 2);
        }
        switch (name) {
            case "java.lang.Boolean":
            case "boolean":
                return Boolean.valueOf(value);
            case "java.lang.Character":
            case "char":
                return value.charAt(0);
            case "java.lang.Byte":
            case "byte":
                return Byte.valueOf(value);
            case "java.lang.Short":
            case "short":
                return Short.valueOf(value);
            case "java.lang.Integer":
            case "int":
                return Integer.valueOf(value);
            case "java.lang.Long":
            case "long":
                return Long.valueOf(value);
            case "java.lang.Float":
            case "float":
                return Float.valueOf(value);
            case "java.lang.Double":
            case "double":
                return Double.valueOf(value);
            case "java.lang.String":
                return value;
            case "java.util.Date":
                try {
                    return DateUtils.parseDate(value, fmt);
                } catch (ParseException e) {
                    e.printStackTrace();
                    throw new ConversionException("无法将格式化日期");
                }
            default:
                throw new ConversionException("无法将String类型" + value + "转为[" + name + "]");
        }
    }

    /**
     * 数组类型的转换
     *
     * @param ctClass 目标类: javassist的ctClass类对象
     * @param value   字符串表示的数组(逗号分隔符)
     * @return Array
     */
    public static Object conversionArray(CtClass ctClass, String value) {
        if (!isBasicTypeArray(ctClass)) throw new ConversionException("无法解析数组");
        String[] strArr = value.split(",");
        List<Object> obj = new ArrayList<>();
        Arrays.stream(strArr).forEach(v -> obj.add(conversion(ctClass, v, null)));

        try {
            // 暂时这么处理
            String name = "[" + ((CtPrimitiveType) ctClass.getComponentType()).getDescriptor();
            Class<?> cls = Class.forName(name).getComponentType();
            Object arr = Array.newInstance(cls, obj.size());//初始化对应类型的数组

            for (int i = 0; i < obj.size(); i++) {
                Array.set(arr, i, obj.get(i));
            }
            return arr;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 判断是否是基本类型 8种原始类型和包装类以及String类型 返回true
     *
     * @return bool
     */
    public static boolean isBasicType(CtClass ctClass) {
        if (ctClass.isPrimitive() || "java.util.Date".equals(ctClass.getName())) {
            return true;
        }
        return ctClass.getName().matches("^java\\.lang\\.((Boolean)|(Character)|(Byte)|(Short)|(Integer)|(Long)|(Float)|(Double)|(String))$");
    }

    /**
     * 判断是否是基本类型数组 8种原始数组类型和String数组 返回true
     *
     * @return bool
     */
    public static boolean isBasicTypeArray(CtClass ctClass) {
        if (!ctClass.isArray()) {
            return false;
        } else return (ctClass.getName().matches("^(boolen|char|byte|short|int|long|float|double|String)\\[]$"));
    }

    /**
     * 反射通过无参构造创建对象
     *
     * @param handler 类对象
     * @return 目标对象
     * @throws NoSuchMethodException     NoSuchMethodException
     * @throws InvocationTargetException InvocationTargetException
     * @throws InstantiationException    InstantiationException
     * @throws IllegalAccessException    IllegalAccessException
     */
    public static <T> T newWithNoParam(Class<T> handler) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return handler.getConstructor().newInstance();
    }

    /**
     * 反射调用有参方法
     *
     * @param method    方法类对象
     * @param instance  方法所在的对象实例
     * @param arguments 方法参数
     * @return 方法返回值
     * @throws Throwable Throwable
     */
    public static Object invokeWithArguments(Method method, Object instance, Object... arguments) throws Throwable {
        return MethodHandles.lookup().unreflect(method).bindTo(instance).invokeWithArguments(arguments);
    }

    /**
     * 反射调用无参方法
     *
     * @param method   方法类对象
     * @param instance 方法所在的对象实例
     * @return 方法返回值
     * @throws Throwable Throwable
     */
    public static Object invoke(Method method, Object instance) throws Throwable {
        return MethodHandles.lookup().unreflect(method).bindTo(instance).invoke();
    }

}
