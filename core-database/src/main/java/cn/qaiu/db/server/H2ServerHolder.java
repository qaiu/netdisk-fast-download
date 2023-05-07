package cn.qaiu.db.server;

import org.h2.tools.Server;

import java.util.Objects;

/**
 * h2db server
 * <br>Create date 2021/7/23 2:44
 *
 * @author <a href="https://qaiu.top">QAIU</a>
 */
public class H2ServerHolder {

    private static Server h2Server;

    public static synchronized void init(Server server) {
        Objects.requireNonNull(server, "未初始化h2Server");
        h2Server = server;
    }

    public static Server getH2Server() {
        Objects.requireNonNull(h2Server, "等待h2Server初始化");
        return h2Server;
    }
}
