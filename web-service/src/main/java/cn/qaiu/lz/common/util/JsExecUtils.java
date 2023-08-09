package cn.qaiu.lz.common.util;

import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;

/**
 * 执行Js脚本
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2023/7/29 17:35
 */
public class JsExecUtils {
    private static final String JS_PATH = "/js/ye123.js";

    /**
     * 调用js文件
     */
    public static ScriptObjectMirror executeJs(String functionName, Object... args) throws ScriptException,
            IOException, NoSuchMethodException {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("JavaScript"); // 得到脚本引擎
        //获取文件所在的相对路径
        URL resource = JsExecUtils.class.getResource("/");
        if (resource == null) {
            throw new ScriptException("js resource path is null");
        }
        String path = resource.getPath();
        String reader = path + JS_PATH;
        FileReader fReader = new FileReader(reader);
        engine.eval(fReader);
        fReader.close();

        Invocable inv = (Invocable) engine;
        //调用js中的方法
        return (ScriptObjectMirror) inv.invokeFunction(functionName, args);
    }
}
