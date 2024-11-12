package cn.qaiu.vx.core.verticle.conf;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.ProxyOptions;

import java.util.UUID;

@DataObject
@JsonGen(publicConverter = false)
public class HttpProxyConf {

    public static final String DEFAULT_USERNAME = UUID.randomUUID().toString();

    public static final String DEFAULT_PASSWORD = UUID.randomUUID().toString();

    public static final Integer DEFAULT_PORT = 6402;

    public static final Integer DEFAULT_TIMEOUT = 15000;

    Integer timeout;

    String username;

    String password;

    Integer port;

    ProxyOptions preProxyOptions;

    public HttpProxyConf() {
        this.username = DEFAULT_USERNAME;
        this.password = DEFAULT_PASSWORD;
        this.timeout = DEFAULT_PORT;
        this.timeout = DEFAULT_TIMEOUT;
        this.preProxyOptions = new ProxyOptions();
    }

    public HttpProxyConf(JsonObject json) {
        this();
    }


    public Integer getTimeout() {
        return timeout;
    }

    public HttpProxyConf setTimeout(Integer timeout) {
        this.timeout = timeout;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public HttpProxyConf setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public HttpProxyConf setPassword(String password) {
        this.password = password;
        return this;
    }

    public Integer getPort() {
        return port;
    }

    public HttpProxyConf setPort(Integer port) {
        this.port = port;
        return this;
    }

    public ProxyOptions getPreProxyOptions() {
        return preProxyOptions;
    }

    public HttpProxyConf setPreProxyOptions(ProxyOptions preProxyOptions) {
        this.preProxyOptions = preProxyOptions;
        return this;
    }
}
