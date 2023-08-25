package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 执行Js脚本
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2023/7/29 17:35
 */
public class JsExecUtils {
    private static final String JS_PATH = "js/ye123.js";
    private static final String LZ_JS_PATH = "js/lz.js";

    private static final String RES_PATH;
    private static final Invocable inv;

    // 初始化脚本引擎
    static {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("JavaScript"); // 得到脚本引擎
        //获取文件所在的相对路径
        URL resource = JsExecUtils.class.getResource("/");
        if (resource == null) {
            throw new RuntimeException("js resource path is null");
        }
        RES_PATH = resource.getPath();
        String reader = RES_PATH + JS_PATH;
        try (FileReader fReader = new FileReader(reader)) {
            engine.eval(fReader);
            fReader.close();
            inv = (Invocable) engine;
        } catch (IOException | ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 调用js文件
     */
    public static ScriptObjectMirror executeJs(String functionName, Object... args) throws ScriptException,
            NoSuchMethodException {
        //调用js中的函数
        return (ScriptObjectMirror) inv.invokeFunction(functionName, args);
    }

    /**
     * 调用执行蓝奏云js文件
     */
    public static ScriptObjectMirror executeDynamicJs(String jsText, String funName) throws ScriptException,
            NoSuchMethodException {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("JavaScript"); // 得到脚本引擎
        try {
            //获取文件所在的相对路径
            Path path;
            try {
                path = Paths.get(RES_PATH + LZ_JS_PATH);
            } catch (RuntimeException ioe) {
                path = Paths.get(RES_PATH.substring(1) + LZ_JS_PATH);
            }
            String jsContent = Files.readString(path) + "\n" + jsText;
            engine.eval(jsContent);
            Invocable inv = (Invocable) engine;
            //调用js中的函数
            if (StringUtils.isNotEmpty(funName)) {
                inv.invokeFunction(funName);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return (ScriptObjectMirror) engine.get("signObj");
    }


}
