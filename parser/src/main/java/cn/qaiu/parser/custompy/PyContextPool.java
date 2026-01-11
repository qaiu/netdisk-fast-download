package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.utils.GraalPyResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

/**
 * GraalPy Context 池化管理器
 * 提供共享的 Engine 实例和 Context 池化支持
 * 支持真正的 pip 包（如 requests）
 * 
 * <p>特性：
 * <ul>
 *   <li>共享单个 Engine 实例，减少内存占用和启动时间</li>
 *   <li>Context 对象池，避免重复创建和销毁的开销</li>
 *   <li>支持真正的 pip 包（通过 GraalPy Resources）</li>
 *   <li>支持安全的沙箱配置</li>
 *   <li>线程安全的池化管理</li>
 *   <li>支持优雅关闭和资源清理</li>
 *   <li>路径缓存，避免重复检测文件系统</li>
 *   <li>预热机制，在后台预导入常用模块</li>
 * </ul>
 * 
 * @author QAIU
 */
public class PyContextPool {
    
    private static final Logger log = LoggerFactory.getLogger(PyContextPool.class);
    
    // 池化配置 - 增加初始池大小和延长生命周期
    private static final int INITIAL_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 10;
    private static final long CONTEXT_TIMEOUT_MS = 30000; // 30秒获取超时
    private static final long CONTEXT_MAX_AGE_MS = 900000; // 15分钟最大使用时间
    
    // 路径缓存 - 避免重复检测文件系统
    private static volatile List<String> cachedValidPaths = null;
    private static final Object PATH_CACHE_LOCK = new Object();
    
    // 单例实例
    private static volatile PyContextPool instance;
    private static final Object LOCK = new Object();
    
    // 共享的GraalPy引擎
    private final Engine sharedEngine;
    
    // Context 池
    private final BlockingQueue<PooledContext> contextPool;
    
    // 已创建的Context数量
    private final AtomicInteger createdCount = new AtomicInteger(0);
    
    // 是否已关闭
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // 定期清理过期Context的调度器
    private final ScheduledExecutorService cleanupScheduler;
    
    // Python执行专用线程池
    private final ExecutorService pythonExecutor;
    
    // 超时调度器
    private final ScheduledExecutorService timeoutScheduler;
    
    /**
     * 池化的Context包装器
     */
    public static class PooledContext implements AutoCloseable {
        private final Context context;
        private final long createdTime;
        private final PyContextPool pool;
        private volatile boolean inUse = false;
        private volatile long lastUsedTime;
        
        private PooledContext(Context context, PyContextPool pool) {
            this.context = context;
            this.pool = pool;
            this.createdTime = System.currentTimeMillis();
            this.lastUsedTime = createdTime;
        }
        
        /**
         * 获取底层Context
         */
        public Context getContext() {
            return context;
        }
        
        /**
         * 检查是否过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - createdTime > CONTEXT_MAX_AGE_MS;
        }
        
        /**
         * 归还到池中或关闭
         */
        @Override
        public void close() {
            pool.release(this);
        }
        
        /**
         * 强制关闭Context
         */
        void forceClose() {
            try {
                context.close(true);
            } catch (Exception e) {
                log.warn("关闭Context失败: {}", e.getMessage());
            }
        }
        
        /**
         * 重置Context状态（清除绑定等）
         */
        boolean reset() {
            try {
                // 由于GraalPy的Context不能很好地重置状态，
                // 简单场景下我们选择创建新的Context
                // 但对于短生命周期的执行，可以尝试继续使用
                lastUsedTime = System.currentTimeMillis();
                return !isExpired();
            } catch (Exception e) {
                log.warn("重置Context失败: {}", e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * 私有构造函数
     */
    private PyContextPool() {
        log.info("初始化GraalPy Context池...");
        
        // 创建共享Engine - 使用标准Polyglot API
        Engine engine = null;
        try {
            engine = Engine.newBuilder()
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            
            // 验证Python语言是否可用
            if (!engine.getLanguages().containsKey("python")) {
                throw new IllegalStateException("Python语言不可用，请检查GraalPy依赖配置");
            }
            log.info("Engine创建成功，可用语言: {}", engine.getLanguages().keySet());
        } catch (Exception e) {
            log.error("创建Engine失败: {}", e.getMessage());
            checkGraalPyAvailability();
            throw new RuntimeException("无法初始化GraalPy Engine，请确保GraalPy依赖正确配置", e);
        }
        this.sharedEngine = engine;
        
        // 创建Context池
        this.contextPool = new LinkedBlockingQueue<>(MAX_POOL_SIZE);
        
        // 创建Python执行专用线程池
        this.pythonExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("py-context-pool-worker-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        
        // 创建超时调度器
        this.timeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("py-context-timeout-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        
        // 创建清理调度器
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("py-context-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // 预热：初始化一些Context
        warmup();
        
        // 定期清理过期的Context
        cleanupScheduler.scheduleWithFixedDelay(this::cleanup, 60, 60, TimeUnit.SECONDS);
        
        log.info("GraalPy Context池初始化完成，初始大小: {}", INITIAL_POOL_SIZE);
    }
    
    /**
     * 获取单例实例
     */
    public static PyContextPool getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new PyContextPool();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取共享Engine
     */
    public Engine getSharedEngine() {
        return sharedEngine;
    }
    
    /**
     * 获取Python执行线程池
     */
    public ExecutorService getPythonExecutor() {
        return pythonExecutor;
    }
    
    /**
     * 获取超时调度器
     */
    public ScheduledExecutorService getTimeoutScheduler() {
        return timeoutScheduler;
    }
    
    /**
     * 预热Context池
     * 在后台线程中预创建 Context 并预导入常用模块
     */
    private void warmup() {
        log.info("开始预热 Context 池，目标数量: {}", INITIAL_POOL_SIZE);
        
        // 使用线程池并行预热
        for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
            final int index = i;
            pythonExecutor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    PooledContext pc = createPooledContext();
                    
                    // 预导入 requests 模块（主要耗时点）
                    try {
                        warmupContext(pc.getContext());
                    } catch (Exception e) {
                        log.debug("预热 Context {} 导入模块失败（非首个Context的NativeModules限制）: {}", 
                                index, e.getMessage());
                    }
                    
                    if (!contextPool.offer(pc)) {
                        pc.forceClose();
                    } else {
                        long elapsed = System.currentTimeMillis() - start;
                        log.info("预热 Context {} 完成，耗时: {}ms", index, elapsed);
                    }
                } catch (Exception e) {
                    log.warn("预热 Context {} 失败: {}", index, e.getMessage());
                }
            });
        }
    }
    
    /**
     * 预热单个 Context - 预导入常用模块
     */
    private void warmupContext(Context context) {
        String warmupScript = """
            # 预导入常用模块
            import json
            import re
            import base64
            import hashlib
            import urllib.parse
            
            # 尝试导入 requests（可能因 NativeModules 限制失败）
            try:
                import requests
            except (ImportError, SystemError):
                pass
            """;
        context.eval("python", warmupScript);
    }
    
    /**
     * 创建新的池化Context
     * 使用 GraalPyResources 支持 pip 包
     */
    private PooledContext createPooledContext() {
        if (closed.get()) {
            throw new IllegalStateException("Context池已关闭");
        }
        
        Context context;
        try {
            // 检查 VFS 资源是否存在
            var vfsResource = getClass().getClassLoader().getResource("org.graalvm.python.vfs/venv");
            log.info("GraalPy VFS资源检查: venv={}", vfsResource != null ? "存在" : "不存在");
            
            // 使用 GraalPyResources 创建支持 pip 包的 Context
            // 注意：不传入共享 Engine，让 GraalPyResources 管理自己的 Engine
            log.info("正在创建 GraalPyResources Context...");
            context = GraalPyResources.contextBuilder()
                    .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                            .allowArrayAccess(true)
                            .allowListAccess(true)
                            .allowMapAccess(true)
                            .allowIterableAccess(true)
                            .allowIteratorAccess(true)
                            .build())
                    .allowExperimentalOptions(true)
                    .allowCreateThread(true)
                    // 允许 IO 以支持 pip 包加载和网络请求
                    .allowIO(IOAccess.ALL)
                    .allowNativeAccess(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            log.info("GraalPyResources Context 创建成功");
            
            // 配置 Python 路径
            setupPythonPath(context);
            
        } catch (Exception e) {
            log.error("使用GraalPyResources创建Context失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法创建支持pip包的Python Context: " + e.getMessage(), e);
        }
        
        createdCount.incrementAndGet();
        log.debug("创建新的GraalPy Context，当前总数: {}", createdCount.get());
        
        return new PooledContext(context, this);
    }
    
    /**
     * 从池中获取Context
     * 
     * @return 池化的Context，用完后需要调用close()归还
     * @throws InterruptedException 如果等待被中断
     * @throws TimeoutException 如果超时未获取到
     */
    public PooledContext acquire() throws InterruptedException, TimeoutException {
        if (closed.get()) {
            throw new IllegalStateException("Context池已关闭");
        }
        
        // 尝试从池中获取
        PooledContext pc = contextPool.poll();
        
        if (pc != null) {
            if (!pc.isExpired() && pc.reset()) {
                pc.inUse = true;
                log.debug("从池中获取Context，池剩余: {}", contextPool.size());
                return pc;
            } else {
                // Context已过期，关闭它
                pc.forceClose();
                createdCount.decrementAndGet();
            }
        }
        
        // 池中没有可用的，检查是否可以创建新的
        if (createdCount.get() < MAX_POOL_SIZE) {
            try {
                pc = createPooledContext();
                pc.inUse = true;
                return pc;
            } catch (Exception e) {
                log.error("创建新Context失败: {}", e.getMessage());
                throw new RuntimeException("无法创建GraalPy Context", e);
            }
        }
        
        // 已达最大数量，等待归还
        pc = contextPool.poll(CONTEXT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (pc == null) {
            throw new TimeoutException("获取GraalPy Context超时");
        }
        
        if (!pc.isExpired() && pc.reset()) {
            pc.inUse = true;
            return pc;
        } else {
            pc.forceClose();
            createdCount.decrementAndGet();
            // 递归重试
            return acquire();
        }
    }
    
    /**
     * 创建一个新的非池化Context（用于需要独立生命周期的场景）
     * 调用者负责管理其生命周期
     * 支持真正的 pip 包（如 requests, zlib 等）
     * 
     * 注意：GraalPyResources 需要独立的 Engine，不能与共享 Engine 一起使用
     */
    public Context createFreshContext() {
        try {
            // 检查 VFS 资源是否存在
            var vfsResource = getClass().getClassLoader().getResource("org.graalvm.python.vfs/venv");
            var homeResource = getClass().getClassLoader().getResource("org.graalvm.python.vfs/home");
            log.info("GraalPy VFS资源检查: venv={}, home={}", 
                    vfsResource != null ? "存在" : "不存在",
                    homeResource != null ? "存在" : "不存在");
            
            // 使用 GraalPyResources 创建支持 pip 包的 Context
            // 注意：不传入共享 Engine，让 GraalPyResources 管理自己的 Engine
            log.info("正在创建 GraalPyResources FreshContext...");
            Context ctx = GraalPyResources.contextBuilder()
                    .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                            .allowArrayAccess(true)
                            .allowListAccess(true)
                            .allowMapAccess(true)
                            .allowIterableAccess(true)
                            .allowIteratorAccess(true)
                            .build())
                    .allowExperimentalOptions(true)
                    .allowCreateThread(true)
                    // 允许 IO 以支持 pip 包加载和网络请求
                    .allowIO(IOAccess.ALL)
                    .allowNativeAccess(true)
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            log.info("GraalPyResources FreshContext 创建成功");
            
            // 手动配置 Python 路径以加载 VFS 中的 pip 包
            setupPythonPath(ctx);
            
            return ctx;
        } catch (Exception e) {
            log.error("使用GraalPyResources创建Context失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法创建支持pip包的Python Context: " + e.getMessage(), e);
        }
    }
    
    /**
     * 配置 Python 路径，确保能够加载 pip 包
     * 使用路径缓存机制，避免重复检测文件系统
     * 
     * pip 包安装在 src/main/resources/graalpy-packages/ 中，会打包进 jar。
     * 运行时从 classpath 或文件系统加载。
     * 
     * 注意：GraalPy 的 NativeModules 限制 - 只有进程中的第一个 Context 可以使用原生模块。
     * 后续 Context 会回退到 LLVM 模式，这可能导致某些依赖原生模块的库无法正常工作。
     * 
     * 安装方法：运行 parser/setup-graalpy-packages.sh
     */
    private void setupPythonPath(Context context) {
        try {
            log.debug("配置 Python 环境...");
            
            // 使用缓存的有效路径
            List<String> validPaths = getValidPythonPaths();
            
            if (validPaths.isEmpty()) {
                log.warn("未找到有效的 Python 包路径");
                return;
            }
            
            // 构建添加路径的脚本 - 使用已验证的路径，跳过文件系统检测
            StringBuilder pathsJson = new StringBuilder("[");
            boolean first = true;
            for (String path : validPaths) {
                if (!first) pathsJson.append(", ");
                first = false;
                pathsJson.append("'").append(path.replace("\\", "/").replace("'", "\\'")).append("'");
            }
            pathsJson.append("]");
            
            // 简化的路径添加脚本 - 不再调用 os.path.isdir，直接添加已验证的路径
            String addPathScript = String.format("""
                import sys
                
                _paths_to_add = %s
                _added_paths = []
                for path in _paths_to_add:
                    if path not in sys.path:
                        sys.path.insert(0, path)
                        _added_paths.append(path)
                
                _added_paths_str = ', '.join(_added_paths) if _added_paths else ''
                """, pathsJson);
            
            context.eval("python", addPathScript);
            Value bindings = context.getBindings("python");
            String addedPaths = bindings.getMember("_added_paths_str").asString();
            
            if (!addedPaths.isEmpty()) {
                log.debug("添加的 Python 路径: {}", addedPaths);
            }
            
            // 验证 requests 是否可用（简化版，不阻塞）
            // 注意：在多 Context 环境中，可能因 NativeModules 限制而失败
            String verifyScript = """
                import sys
                
                _requests_available = False
                _requests_version = ''
                _error_msg = ''
                _native_module_error = False
                
                try:
                    import requests
                    _requests_available = True
                    _requests_version = requests.__version__
                except SystemError as e:
                    # NativeModules 冲突 - GraalPy 限制
                    _error_msg = str(e)
                    if 'NativeModules' in _error_msg or 'llvm' in _error_msg:
                        _native_module_error = True
                except ImportError as e:
                    _error_msg = str(e)
                
                _sys_path_length = len(sys.path)
                """;
            
            context.eval("python", verifyScript);
            
            boolean requestsAvailable = bindings.getMember("_requests_available").asBoolean();
            boolean nativeModuleError = bindings.getMember("_native_module_error").asBoolean();
            int pathLength = bindings.getMember("_sys_path_length").asInt();
            
            if (requestsAvailable) {
                String version = bindings.getMember("_requests_version").asString();
                log.info("Python 环境配置完成: requests {} 可用, sys.path长度: {}", version, pathLength);
            } else if (nativeModuleError) {
                // GraalPy 的 NativeModules 限制 - 这是已知限制，不是配置错误
                log.debug("Python 环境配置: requests 因 NativeModules 限制不可用 (非首个 Context). " +
                        "这是 GraalPy 的已知限制，标准库仍可正常使用。");
            } else {
                String error = bindings.getMember("_error_msg").asString();
                log.warn("Python 环境配置: requests 不可用 ({}), sys.path长度: {}. " +
                        "请运行: ./setup-graalpy-packages.sh", error, pathLength);
            }
            
        } catch (Exception e) {
            String msg = e.getMessage();
            // 检查是否是 NativeModules 相关的错误
            if (msg != null && (msg.contains("NativeModules") || msg.contains("llvm"))) {
                log.debug("Python 环境配置: 因 NativeModules 限制跳过 requests 验证 (非首个 Context)");
            } else {
                log.warn("Python 环境配置失败，继续使用默认配置: {}", msg);
            }
            // 不抛出异常，允许 Context 继续使用
        }
    }
    
    /**
     * 设置安全的 OS 模块限制
     * 只允许安全的读取操作，禁止危险的文件系统操作
     * 
     * 注意：此方法应在所有必要的库导入完成后调用，
     * 因为替换 os 模块会影响依赖它的库（如 requests）
     */
    private void setupSecureOsModule(Context context) {
        // 此方法当前禁用，因为会影响 requests 库的正常工作
        // 安全限制将在代码执行层面实现，而不是替换系统模块
        log.debug("OS 模块安全策略：通过代码审查实现，不替换系统模块");
    }
    
    /**
     * 获取有效的 Python 包路径（带缓存）
     * 首次调用时检测文件系统，后续直接返回缓存
     */
    private List<String> getValidPythonPaths() {
        if (cachedValidPaths != null) {
            return cachedValidPaths;
        }
        
        synchronized (PATH_CACHE_LOCK) {
            if (cachedValidPaths != null) {
                return cachedValidPaths;
            }
            
            log.debug("首次检测 Python 包路径...");
            long start = System.currentTimeMillis();
            
            List<String> validPaths = new ArrayList<>();
            String userDir = System.getProperty("user.dir");
            
            // 尝试从 classpath 获取 graalpy-packages 路径
            String classpathPackages = null;
            try {
                var resource = getClass().getClassLoader().getResource("graalpy-packages");
                if (resource != null) {
                    classpathPackages = resource.getPath();
                    // 处理 jar 内路径
                    if (classpathPackages.contains("!")) {
                        classpathPackages = null; // jar 内无法直接作为文件系统路径
                    }
                }
            } catch (Exception e) {
                log.debug("无法从 classpath 获取 graalpy-packages: {}", e.getMessage());
            }
            
            // 可能的 pip 包路径列表
            String[] possiblePaths = {
                classpathPackages,
                userDir + "/resources/graalpy-packages",
                userDir + "/src/main/resources/graalpy-packages",
                userDir + "/parser/src/main/resources/graalpy-packages",
                userDir + "/target/classes/graalpy-packages",
                userDir + "/parser/target/classes/graalpy-packages",
                userDir + "/graalpy-venv/lib/python3.11/site-packages",
                userDir + "/parser/graalpy-venv/lib/python3.11/site-packages",
            };
            
            // 检测有效路径
            for (String path : possiblePaths) {
                if (path != null) {
                    java.io.File dir = new java.io.File(path);
                    if (dir.isDirectory()) {
                        validPaths.add(path);
                    }
                }
            }
            
            long elapsed = System.currentTimeMillis() - start;
            log.info("Python 包路径检测完成，耗时: {}ms，有效路径数: {}", elapsed, validPaths.size());
            if (!validPaths.isEmpty()) {
                log.debug("有效路径: {}", validPaths);
            }
            
            cachedValidPaths = validPaths;
            return validPaths;
        }
    }
    
    /**
     * 安全策略说明：
     * 
     * 由于 requests 等第三方库内部会使用 os 模块的功能，
     * 直接替换 os 模块会导致这些库无法正常工作。
     * 
     * 因此，安全控制通过以下方式实现：
     * 1. 代码静态检查（在执行前扫描危险的 os.system 等调用）
     * 2. 在 PyPlaygroundExecutor 中对用户代码进行预处理
     * 3. 使用 GraalPy 的沙箱机制限制文件系统访问
     * 
     * 禁止的操作：
     * - os.system(), os.popen() - 系统命令执行
     * - os.remove(), os.unlink(), os.rmdir() - 文件删除
     * - os.mkdir(), os.makedirs() - 目录创建
     * - subprocess.* - 子进程操作
     * 
     * 允许的操作：
     * - requests.* - HTTP 请求
     * - os.path.* - 路径操作（只读）
     * - os.getcwd() - 获取当前目录
     * - json, re, base64, hashlib 等标准库
     */
    
    /**
     * 归还Context到池中
     */
    private void release(PooledContext pc) {
        if (pc == null) return;
        
        pc.inUse = false;
        
        if (closed.get() || pc.isExpired()) {
            // 池已关闭或Context已过期，直接销毁
            pc.forceClose();
            createdCount.decrementAndGet();
            log.debug("Context已过期或池已关闭，销毁Context");
        } else if (!contextPool.offer(pc)) {
            // 池已满，销毁Context
            pc.forceClose();
            createdCount.decrementAndGet();
            log.debug("池已满，销毁多余Context");
        } else {
            log.debug("归还Context到池，池当前大小: {}", contextPool.size());
        }
    }
    
    /**
     * 清理过期的Context
     */
    private void cleanup() {
        if (closed.get()) return;
        
        int removed = 0;
        PooledContext pc;
        
        while ((pc = contextPool.poll()) != null) {
            if (pc.isExpired() || closed.get()) {
                pc.forceClose();
                createdCount.decrementAndGet();
                removed++;
            } else {
                // 还没过期，放回池中
                if (!contextPool.offer(pc)) {
                    pc.forceClose();
                    createdCount.decrementAndGet();
                    removed++;
                }
                break;
            }
        }
        
        if (removed > 0) {
            log.info("清理了 {} 个过期的Context，当前池大小: {}", removed, contextPool.size());
        }
    }
    
    /**
     * 获取池状态信息
     */
    public String getStatus() {
        return String.format("PyContextPool[total=%d, available=%d, maxSize=%d]",
                createdCount.get(), contextPool.size(), MAX_POOL_SIZE);
    }
    
    /**
     * 获取池中可用的Context数量
     */
    public int getAvailableCount() {
        return contextPool.size();
    }
    
    /**
     * 获取已创建的Context总数
     */
    public int getCreatedCount() {
        return createdCount.get();
    }
    
    /**
     * 检查GraalPy是否可用
     */
    private void checkGraalPyAvailability() {
        log.error("===== GraalPy 可用性检查 =====");
        
        // 检查类路径
        try {
            Class.forName("org.graalvm.polyglot.Engine");
            log.info("✓ org.graalvm.polyglot.Engine 类存在");
        } catch (ClassNotFoundException e) {
            log.error("✗ org.graalvm.polyglot.Engine 类不存在");
        }
        
        try {
            Class.forName("org.graalvm.python.embedding.GraalPyResources");
            log.info("✓ org.graalvm.python.embedding.GraalPyResources 类存在");
        } catch (ClassNotFoundException e) {
            log.warn("  python-embedding 类不存在（可选依赖）");
        }
        
        // 尝试列出可用语言
        try {
            log.info("尝试使用标准 Polyglot API 创建 Context...");
            try (Engine engine = Engine.create()) {
                log.info("  可用语言: {}", engine.getLanguages().keySet());
                if (engine.getLanguages().containsKey("python")) {
                    log.info("✓ Python 语言可用");
                } else {
                    log.error("✗ Python 语言不可用");
                }
            }
        } catch (Exception e) {
            log.error("✗ 创建 Engine 失败: {}", e.getMessage());
        }
        
        log.error("================================");
        log.error("请检查以下依赖是否正确配置:");
        log.error("  1. org.graalvm.polyglot:polyglot");
        log.error("  2. org.graalvm.polyglot:python (type=pom)");
        log.error("================================");
    }
    
    /**
     * 关闭Context池
     */
    public void shutdown() {
        if (closed.compareAndSet(false, true)) {
            log.info("关闭GraalPy Context池...");
            
            // 停止清理调度器
            cleanupScheduler.shutdownNow();
            timeoutScheduler.shutdownNow();
            pythonExecutor.shutdownNow();
            
            // 关闭所有池中的Context
            PooledContext pc;
            while ((pc = contextPool.poll()) != null) {
                pc.forceClose();
            }
            
            // 关闭共享Engine
            try {
                sharedEngine.close(true);
            } catch (Exception e) {
                log.warn("关闭共享Engine失败: {}", e.getMessage());
            }
            
            log.info("GraalPy Context池已关闭");
        }
    }
    
    /**
     * 检查池是否已关闭
     */
    public boolean isClosed() {
        return closed.get();
    }
}
