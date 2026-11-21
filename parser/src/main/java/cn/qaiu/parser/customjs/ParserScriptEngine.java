package cn.qaiu.parser.customjs;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

final class ParserScriptEngine {
    private ParserScriptEngine() {}

    static ScriptEngine create() throws ScriptException {
        ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine(
                new String[]{"--no-java"}, null, new SecurityClassFilter());
        // Remove host loaders and scripting-context globals before binding the parser APIs.
        engine.eval("""
                (function (global) {
                    ["load", "loadWithNewGlobal", "exit", "quit", "engine", "context",
                     "Java", "JavaImporter", "Packages", "java", "javax", "javafx", "org",
                     "com", "edu"].forEach(function (name) {
                        Object.defineProperty(global, name, {
                            value: void 0, writable: false, configurable: false
                        });
                    });
                })(this);
                """);
        return engine;
    }
}
