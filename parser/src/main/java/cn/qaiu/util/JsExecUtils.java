package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * 执行Js脚本
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2023/7/29 17:35
 */
public class JsExecUtils {
    private static final Invocable inv;

    // 初始化脚本引擎
    static {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("JavaScript"); // 得到脚本引擎

        try {
            engine.eval(JsContent.ye123);
            inv = (Invocable) engine;
        } catch (ScriptException e) {
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
        engine.eval(JsContent.lz);
        Invocable inv = (Invocable) engine;
        //调用js中的函数
        if (StringUtils.isNotEmpty(funName)) {
            inv.invokeFunction(funName);
        }

        return (ScriptObjectMirror) engine.get("signObj");
    }


}
