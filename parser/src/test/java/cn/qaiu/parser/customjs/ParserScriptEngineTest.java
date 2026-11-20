package cn.qaiu.parser.customjs;

import cn.qaiu.WebClientVertxInit;
import cn.qaiu.entity.ShareLinkInfo;
import cn.qaiu.parser.custom.CustomParserConfig;
import io.vertx.core.Vertx;
import org.junit.Test;

import javax.script.ScriptEngine;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ParserScriptEngineTest {
    @Test
    public void disablesClassLookupAndHostLoadersBeforeRunningScripts() throws Exception {
        SecurityClassFilter filter = new SecurityClassFilter();
        assertFalse(filter.exposeToScripts("java.lang.String"));
        assertFalse(filter.exposeToScripts("java.util.Formatter"));
        ScriptEngine engine = ParserScriptEngine.create();
        for (String name : new String[]{"Java", "JavaImporter", "Packages", "java", "javax",
                "org", "com", "edu", "javafx", "load", "loadWithNewGlobal", "engine", "context", "exit", "quit"}) {
            assertEquals(name, "undefined", engine.eval("typeof " + name));
            assertEquals(name, Boolean.FALSE, engine.eval("Object.getOwnPropertyDescriptor(this, '" + name + "').writable"));
            assertEquals(name, Boolean.FALSE, engine.eval("Object.getOwnPropertyDescriptor(this, '" + name + "').configurable"));
        }
    }

    @Test
    public void ordinaryScriptsCanStillCallAnInjectedParserApi() throws Exception {
        ScriptEngine engine = ParserScriptEngine.create();
        engine.put("logger", new JsPlaygroundLogger());
        assertEquals("ok", engine.eval("logger.info('parser test'); JSON.parse('{\"result\":\"ok\"}').result"));
    }

    @Test
    public void bothExecutorsPreserveParserApisAndDisableHostGlobals() throws Exception {
        String script = """
                function parse(info, http, logger) {
                    logger.info("parser test");
                    return [info.getShareKey(), typeof fetch, typeof java, typeof loadWithNewGlobal].join("|");
                }
                """;
        String expected = "sample|function|undefined|undefined";
        ShareLinkInfo info = ShareLinkInfo.newBuilder().shareKey("sample").build();
        CustomParserConfig config = CustomParserConfig.builder()
                .type("sandbox_test").displayName("Sandbox test").isJsParser(true).jsCode(script).build();
        Vertx vertx = Vertx.vertx();
        try {
            WebClientVertxInit.init(vertx);
            try (JsPlaygroundExecutor playground = new JsPlaygroundExecutor(info, script);
                 JsParserExecutor registered = new JsParserExecutor(info, config)) {
                assertEquals(expected, playground.executeParseAsync().toCompletionStage().toCompletableFuture()
                        .get(10, TimeUnit.SECONDS));
                assertEquals(expected, registered.parse().toCompletionStage().toCompletableFuture()
                        .get(10, TimeUnit.SECONDS));
            }
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }
}
