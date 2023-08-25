package cn.qaiu.web.test;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class Test02 {


    public String[] getParameterName(Class<?> className, String method) {
        String[] paramNames = null;
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get(className.getName());
            CtMethod cm = ctClass.getDeclaredMethod(method);
            MethodInfo methodInfo = cm.getMethodInfo();
            CtClass[] parameterTypes = cm.getParameterTypes();

            for (CtClass parameterType : parameterTypes) {
                System.out.println(parameterType.getDeclaringClass());
                System.out.println(parameterType.getName() + "----" + parameterType.getSimpleName());
            }
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
            LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute
                    .getAttribute(LocalVariableAttribute.tag);
            paramNames = new String[cm.getParameterTypes().length];
            CtClass[] exceptionTypes = cm.getExceptionTypes();
            ExceptionsAttribute exceptionsAttribute = methodInfo.getExceptionsAttribute();

            for (int j = 0; j < paramNames.length; j++) {
                String s = attr.variableName(attr.tableLength() - paramNames.length + j);
                paramNames[j] = s;
            }

        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return paramNames;
    }

    @Test
    public void test01() throws NoSuchMethodException {

        //
//		Method[] methods = RealUser.class.getMethods();
//		for (Method m : methods) {
//		    if (m.getName().equals("setUsername2")) {
//				Class<?>[] parameterTypes = m.getParameterTypes();
//				for (Class<?> type : parameterTypes) {
//					System.out.println(type + "--"+type.getName());
//					System.out.println(type.isPrimitive());
//					System.out.println("------------");
//				}
//			}
//		}
    }

    @Test
    public void test2() {
        System.out.println(("java.lang.Double".matches("^java\\.lang\\.((Integer)|(Double))$")));
    }


    @Test
    public void test3() {
        Map map = new LinkedHashMap();
        map.put("1", "1");
        map.put("2", "11");
        map.put("3", "111");

        System.out.println(map);
        map.put("1", "12");
        System.out.println(map);

    }


    @Test
    public void test4() {
        LocalDateTime parse = LocalDateTime.parse((String) "2022-01-01T11:22:00");
        System.out.println(parse);

    }


}
