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
        engine.eval(JsContent.lz + "\n" + jsText);
        Invocable inv = (Invocable) engine;
        //调用js中的函数
        if (StringUtils.isNotEmpty(funName)) {
            inv.invokeFunction(funName);
        }

        return (ScriptObjectMirror) engine.get("signObj");
    }


    /**
     * 调用执行蓝奏云js文件
     */
    public static Object executeOtherJs(String jsText, String funName, Object ... args) throws ScriptException,
            NoSuchMethodException {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("JavaScript"); // 得到脚本引擎
        engine.eval(jsText);
        Invocable inv = (Invocable) engine;
        //调用js中的函数
        if (StringUtils.isNotEmpty(funName)) {
            return inv.invokeFunction(funName, args);
        }
        throw new ScriptException("funName is null");
    }

    public static String getKwSign(String s, String pwd) {
        try {
            return executeOtherJs(JsContent.kwSignString, "encrypt", s, pwd).toString();
        } catch (ScriptException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        //encrypt("ZYcEEs2JdncXG8zAaytJiXxmbyhH2wxb", "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324")
        // 1a9b1a33222a78d6506e0aeaacf5b9b69984954de79e98e3ef4766c009025b7000000000
        // acb0a82caa6ee641ca99ad81ace7081f58412e2148619827aa0a038a8d76c75000000000
        // f7a05b893131238ee4d1f31a85401b64056bb09988b5b9c2b87c12542578360600000000
        System.out.println(getKwSign("c7nkKBeXXzCyTQ8Wc8DRNYc4Th3f6hTE", "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324"));

    }

}
