package cn.qaiu.util;

import org.apache.commons.lang3.StringUtils;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.qaiu.util.AESUtils.encrypt;

/**
 * 执行Js脚本
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 * Create at 2023/7/29 17:35
 */
public class JsExecUtils {
    private static final Invocable inv;
    private static final ScriptEngineManager ENGINE_MANAGER = new ScriptEngineManager();

    // 初始化脚本引擎
    static {
        ScriptEngine engine = ENGINE_MANAGER.getEngineByName("JavaScript"); // 得到脚本引擎

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
     * 调用执行蓝奏云js文件（每次动态JS代码，无法复用引擎）
     * 注意：使用后清理引擎引用，帮助 GC 回收 Nashorn 引擎内部资源
     */
    public static ScriptObjectMirror executeDynamicJs(String jsText, String funName) throws ScriptException,
            NoSuchMethodException {
        ScriptEngine engine = ENGINE_MANAGER.getEngineByName("JavaScript"); // 得到脚本引擎
        try {
            engine.eval(JsContent.lz + "\n" + jsText);
            Invocable inv = (Invocable) engine;
            //调用js中的函数
            if (StringUtils.isNotEmpty(funName)) {
                inv.invokeFunction(funName);
            }
            return (ScriptObjectMirror) engine.get("signObj");
        } finally {
            // 清理引擎持有的引用，帮助 GC 回收
            clearEngineBindings(engine);
        }
    }


    /**
     * 调用执行js文件（使用缓存的 ScriptEngineManager 创建新引擎实例）
     * 注意：使用后清理引擎引用，帮助 GC 回收 Nashorn 引擎内部资源
     */
    public static Object executeOtherJs(String jsText, String funName, Object ... args) throws ScriptException,
            NoSuchMethodException {
        ScriptEngine engine = ENGINE_MANAGER.getEngineByName("JavaScript"); // 得到脚本引擎
        try {
            engine.eval(jsText);
            Invocable inv = (Invocable) engine;
            //调用js中的函数
            if (StringUtils.isNotEmpty(funName)) {
                return inv.invokeFunction(funName, args);
            }
            throw new ScriptException("funName is null");
        } finally {
            // 清理引擎持有的引用，帮助 GC 回收
            clearEngineBindings(engine);
        }
    }

    /**
     * 清理 ScriptEngine 的 bindings，帮助 GC 回收 Nashorn 引擎资源
     */
    private static void clearEngineBindings(ScriptEngine engine) {
        try {
            if (engine != null) {
                // 清理全局 bindings
                var bindings = engine.getBindings(javax.script.ScriptContext.ENGINE_SCOPE);
                if (bindings != null) {
                    bindings.clear();
                }
            }
        } catch (Exception ignored) {
            // 清理失败不影响主流程
        }
    }

    public static String getKwSign(String s, String pwd) {
        try {
            return executeOtherJs(JsContent.kwSignString, "encrypt", s, pwd).toString();
        } catch (ScriptException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static String mgEncData(String data, String key) {
        try {
            return executeOtherJs(JsContent.mgJS, "enc", data, key).toString();
        } catch (ScriptException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    // return OM.AES.encrypt('{"copyrightId":"6326951FKBL","type":1,"auditionsFlag":0}',
    // '4ea5c508a6566e76240543f8feb06fd457777be39549c4016436afda65d2330e').toString()
    //

    public static void main(String[] args) {
        System.out.println(URLEncoder
                .encode(mgEncData("{\"copyrightId\":\"6326951FKBL\",\"type\":1,\"auditionsFlag\":0}", AESUtils.MG_KEY), StandardCharsets.UTF_8));

        // U2FsdGVkX1/UiZC91ImQvQY7qDBSEbTykAcVoARiquibPCZhWSs3kWQw3j2PNme5wNLqt2oau498ni1hgjGFuxwORnkk6x9rzk/X0arElUo=

    }

}
