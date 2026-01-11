package cn.qaiu.lz.web.controller;

import cn.qaiu.lz.web.config.PlaygroundConfig;
import cn.qaiu.vx.core.annotaions.RouteHandler;
import cn.qaiu.vx.core.annotaions.SockRouteMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.handler.sockjs.SockJSSocket;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Python LSP (pylsp/jedi) WebSocket 桥接处理器
 * 
 * 通过 WebSocket 将前端 LSP 请求转发到 pylsp 子进程，
 * 实现实时代码检查、自动完成、悬停提示等功能。
 * 
 * 使用 jedi 的 python-lsp-server (pylsp)，需要预先安装:
 * pip install python-lsp-server[all]
 * 
 * @author <a href="https://qaiu.top">QAIU</a>
 */
@RouteHandler(value = "/v2/ws")
@Slf4j
public class PylspWebSocketHandler {

    // 存储每个 WebSocket 连接对应的 pylsp 进程
    private static final ConcurrentHashMap<String, PylspSession> sessions = new ConcurrentHashMap<>();

    /**
     * WebSocket LSP 端点
     * 前端通过此端点连接，发送 LSP JSON-RPC 消息
     */
    @SockRouteMapper("/pylsp")
    public void handlePylsp(SockJSSocket socket) {
        String sessionId = socket.writeHandlerID();
        log.info("========================================");
        log.info("[PYLSP] WebSocket Handler 被调用!");
        log.info("[PYLSP] Session ID: {}", sessionId);
        log.info("[PYLSP] Remote Address: {}", socket.remoteAddress());
        log.info("========================================");

        // 检查 Playground 是否启用
        PlaygroundConfig config = PlaygroundConfig.getInstance();
        log.info("[PYLSP] Playground enabled: {}", config.isEnabled());
        log.info("[PYLSP] Playground public: {}", config.isPublic());
        
        if (!config.isEnabled()) {
            log.error("[PYLSP] Playground功能已禁用! 请检查配置文件中 playground.enabled 设置");
            log.error("[PYLSP] 当前配置: enabled={}, public={}", config.isEnabled(), config.isPublic());
            socket.write(Buffer.buffer("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Playground功能已禁用，请联系管理员\"},\"id\":null}"));
            socket.close();
            return;
        }

        // 创建 pylsp 会话
        PylspSession session = new PylspSession(socket, sessionId);
        sessions.put(sessionId, session);

        // 启动 pylsp 进程
        if (!session.start()) {
            socket.write(Buffer.buffer("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"无法启动pylsp服务\"},\"id\":null}"));
            socket.close();
            sessions.remove(sessionId);
            return;
        }

        // 处理来自前端的消息
        socket.handler(buffer -> {
            String message = buffer.toString(StandardCharsets.UTF_8);
            log.debug("收到 LSP 请求: {}", message);
            session.sendToLsp(message);
        });

        // 处理连接关闭
        socket.endHandler(v -> {
            log.info("pylsp WebSocket 连接关闭: {}", sessionId);
            session.stop();
            sessions.remove(sessionId);
        });

        // 处理异常
        socket.exceptionHandler(e -> {
            log.error("pylsp WebSocket 异常: {}", sessionId, e);
            session.stop();
            sessions.remove(sessionId);
        });
    }

    /**
     * 查找 graalpy-packages 目录路径
     * 支持多种运行环境：开发环境、IDE 运行、jar 包运行
     */
    private static String findGraalPyPackagesPath(String userDir) {
        // 按优先级尝试多个可能的路径
        String[] possiblePaths = {
            // 开发环境 - IDE 直接运行
            userDir + "/parser/src/main/resources/graalpy-packages",
            // Maven 编译后路径
            userDir + "/parser/target/classes/graalpy-packages",
            // jar 包同级目录
            userDir + "/graalpy-packages",
            // jar 包运行时的 resources 目录
            userDir + "/resources/graalpy-packages",
            // 相对于 web-service 模块
            userDir + "/../parser/src/main/resources/graalpy-packages",
            // 从 web-service/target/package 向上查找
            userDir + "/../../parser/src/main/resources/graalpy-packages",
            userDir + "/../../../parser/src/main/resources/graalpy-packages",
        };
        
        for (String path : possiblePaths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File pylspModule = new File(dir, "pylsp");
                if (pylspModule.exists()) {
                    try {
                        String canonicalPath = dir.getCanonicalPath();
                        log.info("[PYLSP] 找到 graalpy-packages: {}", canonicalPath);
                        return canonicalPath;
                    } catch (IOException e) {
                        log.warn("[PYLSP] 获取规范路径失败: {}", path);
                        return dir.getAbsolutePath();
                    }
                }
            }
        }
        
        // 打印尝试的所有路径用于调试
        log.error("[PYLSP] 尝试的路径:");
        for (String path : possiblePaths) {
            File dir = new File(path);
            log.error("[PYLSP]   {} (exists={})", path, dir.exists());
        }
        
        return null;
    }

    /**
     * pylsp 会话管理类
     * 管理单个 pylsp 子进程和对应的 WebSocket 连接
     */
    private static class PylspSession {
        private final SockJSSocket socket;
        private final String sessionId;
        private Process process;
        private BufferedWriter processWriter;
        private Thread readerThread;
        private final AtomicBoolean running = new AtomicBoolean(false);

        public PylspSession(SockJSSocket socket, String sessionId) {
            this.socket = socket;
            this.sessionId = sessionId;
        }

        /**
         * 启动 pylsp 子进程
         * 
         * 使用 GraalPy 和打包在 jar 中的 python-lsp-server。
         * graalpy-packages 中包含完整的 pylsp 依赖。
         */
        public boolean start() {
            try {
                // 检测运行环境（开发环境 vs jar 包）
                String userDir = System.getProperty("user.dir");
                String graalPyPackagesPath = findGraalPyPackagesPath(userDir);
                
                if (graalPyPackagesPath == null) {
                    log.error("[PYLSP] 找不到 graalpy-packages 目录!");
                    log.error("[PYLSP] 已尝试的路径: {}", userDir);
                    log.error("[PYLSP] 请运行: parser/setup-graalpy-packages.sh");
                    return false;
                }
                
                // 检查 pylsp 是否存在
                File pylspModule = new File(graalPyPackagesPath + "/pylsp");
                if (!pylspModule.exists()) {
                    log.error("[PYLSP] pylsp 模块不存在: {}", pylspModule.getAbsolutePath());
                    log.error("[PYLSP] 请运行: parser/setup-graalpy-packages.sh");
                    return false;
                }
                
                // 使用系统 Python (因为 GraalPy 不支持作为独立进程运行 pylsp)
                // 但通过 PYTHONPATH 使用打包的 pylsp
                ProcessBuilder pb = new ProcessBuilder(
                    "python3", "-m", "pylsp",
                    "-v"      // 详细日志
                );
                
                // 设置环境变量
                var env = pb.environment();
                env.put("PYTHONPATH", graalPyPackagesPath);
                log.info("[PYLSP] PYTHONPATH: {}", graalPyPackagesPath);
                
                pb.redirectErrorStream(false);
                process = pb.start();
                
                processWriter = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)
                );

                running.set(true);

                // 启动读取线程，将 pylsp 的输出转发到 WebSocket
                readerThread = new Thread(() -> readLspOutput(), "pylsp-reader-" + sessionId);
                readerThread.setDaemon(true);
                readerThread.start();

                // 启动错误读取线程
                Thread errorThread = new Thread(() -> readLspError(), "pylsp-error-" + sessionId);
                errorThread.setDaemon(true);
                errorThread.start();

                log.info("[PYLSP] pylsp 进程已启动 (Session: {})", sessionId);
                log.info("[PYLSP] 进程 PID: {}", process.pid());
                return true;

            } catch (Exception e) {
                log.error("[PYLSP] 启动 pylsp 进程失败", e);
                log.error("[PYLSP] 错误详情: {}", e.getMessage());
                log.error("[PYLSP] 请确保:");
                log.error("[PYLSP]   1. 已运行 parser/setup-graalpy-packages.sh");
                log.error("[PYLSP]   2. 系统已安装 python3");
                log.error("[PYLSP]   3. graalpy-packages 中包含 pylsp 模块");
                return false;
            }
        }

        /**
         * 发送消息到 pylsp 进程
         */
        public void sendToLsp(String message) {
            if (!running.get() || processWriter == null) {
                return;
            }

            try {
                // LSP 协议: Content-Length: xxx\r\n\r\n{json}
                byte[] contentBytes = message.getBytes(StandardCharsets.UTF_8);
                String header = "Content-Length: " + contentBytes.length + "\r\n\r\n";
                
                processWriter.write(header);
                processWriter.write(message);
                processWriter.flush();
                
                log.debug("发送到 pylsp: {}", message);
            } catch (IOException e) {
                log.error("发送消息到 pylsp 失败", e);
            }
        }

        /**
         * 读取 pylsp 输出并转发到 WebSocket
         */
        private void readLspOutput() {
            try {
                InputStream inputStream = process.getInputStream();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );

                while (running.get()) {
                    // 读取 LSP 头部
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }

                    // 解析 Content-Length
                    int contentLength = -1;
                    while (line != null && !line.isEmpty()) {
                        if (line.startsWith("Content-Length:")) {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        }
                        line = reader.readLine();
                    }

                    if (contentLength > 0) {
                        // 读取 JSON 内容
                        char[] content = new char[contentLength];
                        int read = 0;
                        while (read < contentLength) {
                            int r = reader.read(content, read, contentLength - read);
                            if (r == -1) break;
                            read += r;
                        }

                        String jsonContent = new String(content);
                        log.debug("pylsp 响应: {}", jsonContent);

                        // 发送到 WebSocket
                        if (socket != null && running.get()) {
                            socket.write(Buffer.buffer(jsonContent));
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("读取 pylsp 输出失败", e);
                }
            }
        }

        /**
         * 读取 pylsp 错误输出
         */
        private void readLspError() {
            try {
                InputStream errorStream = process.getErrorStream();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errorStream, StandardCharsets.UTF_8)
                );

                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    log.debug("pylsp stderr: {}", line);
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("读取 pylsp 错误输出失败", e);
                }
            }
        }

        /**
         * 停止 pylsp 会话
         */
        public void stop() {
            running.set(false);

            try {
                if (processWriter != null) {
                    processWriter.close();
                }
            } catch (IOException e) {
                // ignore
            }

            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    // 等待进程结束
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                }
            }

            log.info("pylsp 会话已停止: {}", sessionId);
        }
    }

    /**
     * 获取当前活跃的 pylsp 会话数
     */
    public static int getActiveSessionCount() {
        return sessions.size();
    }
}
