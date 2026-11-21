package cn.qaiu.parser.customjs;

import org.openjdk.nashorn.api.scripting.ClassFilter;

/** Scripts work through the supplied parser APIs and may not resolve Java classes by name. */
public class SecurityClassFilter implements ClassFilter {
    @Override
    public boolean exposeToScripts(String className) {
        return false;
    }
}
