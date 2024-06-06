package cn.qaiu.web.test;

import io.vertx.ext.web.RoutingContext;
import org.apache.commons.beanutils2.BeanUtils;
import org.apache.commons.beanutils2.ConvertUtils;
import org.apache.commons.beanutils2.Converter;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <br>Create date 2021/4/29 15:27
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class Test01 {

    public static class A {
        String name;
        String num;
        String num2;
        String num3;

        Integer num5;
        public Integer getNum5() {
            return num5;
        }

        public void setNum5(Integer num5) {
            this.num5 = num5;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNum() {
            return num;
        }

        public void setNum(String num) {
            this.num = num;
        }

        public String getNum2() {
            return num2;
        }

        public void setNum2(String num2) {
            this.num2 = num2;
        }

        public String getNum3() {
            return num3;
        }

        public void setNum3(String num3) {
            this.num3 = num3;
        }
    }


    public static class B0{
        int num;

        public int getNum() {
            return num;
        }

        public void setNum(int num) {
            this.num = num;
        }

    }

    public static class B extends B0{
        String name;

        boolean flag;
        int num4;
        Date date;
        String dateStr;

        Integer num5;

        public Boolean getFlag() {
            return flag;
        }

        public void setFlag(Boolean flag) {
            this.flag = flag;
        }

        public Integer getNum5() {
            return num5;
        }

        public void setNum5(Integer num5) {
            this.num5 = num5;
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public String getDateStr() {
            return dateStr;
        }

        public void setDateStr(String dateStr) {
            this.dateStr = dateStr;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getNum4() {
            return num4;
        }

        public void setNum4(int num4) {
            this.num4 = num4;
        }

        @Override
        public String toString() {
            return "B{" +
                    "num=" + num +
                    ", name='" + name + '\'' +
                    ", flag=" + flag +
                    ", num4=" + num4 +
                    ", date=" + date +
                    ", dateStr='" + dateStr + '\'' +
                    ", num5=" + num5 +
                    '}';
        }
    }


    public static <T> T getParamsToBean(RoutingContext ctx, Class<T> tClass) {
//        ObjectUtils.identityToString()
        return null;
    }

    @Test
    public void test01() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        A a = new A();
        a.setName("asd");
        a.setNum("123");
        a.setNum2("123");
        a.setNum3("123");
        a.setNum5(9999);
        B b = new B();
        BeanUtils.copyProperties(b, a);
        System.out.println(b);
        a.setNum5(233);
        System.out.println(b);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "小米");
        map.put("flag", "1");
        map.put("num", "553454344");
        map.put("num2", "123");
        map.put("num4", "q");
        map.put("dateStr", new Date());
        map.put("date", "2021-01-01");
        B b1 = new B();

        ConvertUtils.register(
                new Converter() {
                    @Override
                    public <T> T convert(Class<T> clazz, Object value) {
                        //字符串转换为日期
                        try {
                            return (T) DateUtils.parseDate(value.toString(), "yyyy-MM-dd");
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                }, Date.class);


        ConvertUtils.register(
                new Converter() {
                    @Override
                    public <T> T convert(Class<T> clazz, Object value) {
                        //日期->字符串
                        try {
                            return (T) DateFormatUtils.format((Date) value, "yyyy-MM-dd");
                        }catch (Exception e){
                            return (T)value;
                        }
                    }
                }, String.class);

        BeanUtils.populate(b1, map);
        System.out.println(b1);
    }

}
