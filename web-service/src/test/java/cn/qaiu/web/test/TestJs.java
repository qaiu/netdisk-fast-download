package cn.qaiu.web.test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2023/7/29 17:15
 */
public class TestJs {

    /**
     * 调用js文件获取url
     *
     */
    private static String excuteJs() throws ScriptException,
            IOException, NoSuchMethodException {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("JavaScript"); // 得到脚本引擎
        String reader = null;
        //获取文件所在的相对路径
        //String text = System.getProperty("user.dir");
        //reader = text + "\\src\\main\\resources\\test.js";

        String path = TestJs.class.getResource("/").getPath();
        System.out.println(path);
        reader = path + "/test.js";
        FileReader fReader = new FileReader(reader);
        engine.eval(fReader);

        Invocable inv = (Invocable) engine;
        //调用js中的方法
        Object test2 = inv.invokeFunction("add", 1, 2);
        String url = test2.toString();
        fReader.close();
        return url;
    }

    public static void main(String[] args) throws ScriptException, IOException, NoSuchMethodException {
        String s = excuteJs();
        System.out.println(s);
    }
}
