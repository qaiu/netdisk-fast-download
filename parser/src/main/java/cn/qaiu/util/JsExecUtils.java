package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import static cn.qaiu.util.AESUtils.encrypt;

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
        //'7909e8e754545a61ba4bc3c90c82cb6c69b6859d5ea2e46a6bf913d1b4f11dee011dced1'
        // 1e3170f1cc1ca75172409e443b89261ec777e190ebc595b458b8e114a912a9544d2b467323f8ca011b2ed0
        // 93a44ef48949d950c91303c84d36
        // 95dea502a45fb153f68d8da0bf8e4a095a001e396f60837e9c1b58a48969eb77038234d2
        // 93c3750f6ccf9d11b5c304b32495
        System.out.println(getKwSign("Hm_lvt_cdb524f42f0ce19b169a8071123a4797", "1729503755"));
        System.out.println(getKwSign("HelloWorld", "password123"));

    }

}
