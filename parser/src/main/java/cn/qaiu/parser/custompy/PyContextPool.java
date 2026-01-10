package cn.qaiu.parser.custompy;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GraalPy Context 池化管理器
 * 提供共享的 Engine 实例和 Context 池化支持
 * 
 * <p>特性：
 * <ul>
 *   <li>共享单个 Engine 实例，减少内存占用和启动时间</li>
 *   <li>Context 对象池，避免重复创建和销毁的开销</li>
 *   <li>支持安全的沙箱配置</li>
 *   <li>线程安全的池化管理</li>
 *   <li>支持优雅关闭和资源清理</li>
 * </ul>
 * 
 * @author QAIU
 */
public class PyContextPool {
    
    private static final Logger log = LoggerFactory.getLogger(PyContextPool.class);
    
    // 池化配置
    private static final int INITIAL_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 10;
    private static final long CONTEXT_TIMEOUT_MS = 30000; // 30秒获取超时
    private static final long CONTEXT_MAX_AGE_MS = 300000; // 5分钟最大使用时间
    
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
        
        // 创建共享Engine - 优先使用 GraalPyResources
        Engine engine = null;
        try {
            // 使用 GraalPyResources 创建，这是推荐的方式
            var tempContext = org.graalvm.python.embedding.GraalPyResources.contextBuilder()
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
            engine = tempContext.getEngine();
            tempContext.close();
            log.info("Engine创建成功（GraalPyResources模式）");
        } catch (Exception e) {
            log.warn("GraalPyResources模式创建Engine失败，尝试标准方式: {}", e.getMessage());
            try {
                engine = Engine.newBuilder()
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();
                log.info("Engine创建成功（标准模式）");
            } catch (Exception e2) {
                log.error("标准模式创建Engine也失败: {}", e2.getMessage());
                checkGraalPyAvailability();
                throw new RuntimeException("无法初始化GraalPy Engine，请确保GraalPy依赖正确配置", e2);
            }
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
     */
    private void warmup() {
        for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
            try {
                PooledContext pc = createPooledContext();
                if (!contextPool.offer(pc)) {
                    pc.forceClose();
                }
            } catch (Exception e) {
                log.warn("预热Context失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 创建新的池化Context
     */
    private PooledContext createPooledContext() {
        if (closed.get()) {
            throw new IllegalStateException("Context池已关闭");
        }
        
        Context context;
        try {
            // 首先尝试使用共享Engine创建
            context = Context.newBuilder("python")
                    .engine(sharedEngine)
                    .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                            .allowArrayAccess(true)
                            .allowListAccess(true)
                            .allowMapAccess(true)
                            .allowIterableAccess(true)
                            .allowIteratorAccess(true)
                            .build())
                    .allowHostClassLookup(className -> false)
                    .allowExperimentalOptions(true)
                    .allowCreateThread(true)
                    .allowNativeAccess(false)
                    .allowCreateProcess(false)
                    .allowIO(IOAccess.newBuilder()
                            .allowHostFileAccess(false)
                            .allowHostSocketAccess(false)
                            .build())
                    .option("python.PythonHome", "")
                    .option("python.ForceImportSite", "false")
                    .build();
        } catch (Exception e) {
            log.warn("使用共享Engine创建Context失败，尝试使用GraalPyResources: {}", e.getMessage());
            // 使用 GraalPyResources 作为备选
            context = org.graalvm.python.embedding.GraalPyResources.contextBuilder()
                    .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                            .allowArrayAccess(true)
                            .allowListAccess(true)
                            .allowMapAccess(true)
                            .allowIterableAccess(true)
                            .allowIteratorAccess(true)
                            .build())
                    .allowHostClassLookup(className -> false)
                    .allowExperimentalOptions(true)
                    .allowCreateThread(true)
                    .allowNativeAccess(false)
                    .allowCreateProcess(false)
                    .allowIO(IOAccess.newBuilder()
                            .allowHostFileAccess(false)
                            .allowHostSocketAccess(false)
                            .build())
                    .build();
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
     */
    public Context createFreshContext() {
        return Context.newBuilder("python")
                .engine(sharedEngine)
                .allowHostAccess(HostAccess.newBuilder(HostAccess.EXPLICIT)
                        .allowArrayAccess(true)
                        .allowListAccess(true)
                        .allowMapAccess(true)
                        .allowIterableAccess(true)
                        .allowIteratorAccess(true)
                        .build())
                .allowHostClassLookup(className -> false)
                .allowExperimentalOptions(true)
                .allowCreateThread(true)
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .allowIO(IOAccess.newBuilder()
                        .allowHostFileAccess(false)
                        .allowHostSocketAccess(false)
                        .build())
                .option("python.PythonHome", "")
                .option("python.ForceImportSite", "false")
                .build();
    }
    
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
            log.error("✗ org.graalvm.python.embedding.GraalPyResources 类不存在 - 缺少 python-embedding 依赖");
        }
        
        // 尝试列出可用语言
        try {
            log.info("尝试使用 GraalPyResources 创建 Context...");
            var context = org.graalvm.python.embedding.GraalPyResources.createContext();
            log.info("✓ GraalPyResources 创建 Context 成功");
            context.close();
        } catch (Exception e) {
            log.error("✗ GraalPyResources 创建 Context 失败: {}", e.getMessage());
        }
        
        log.error("================================");
        log.error("请检查以下依赖是否正确配置:");
        log.error("  1. org.graalvm.polyglot:polyglot");
        log.error("  2. org.graalvm.polyglot:python (type=pom)");
        log.error("  3. org.graalvm.python:python-embedding");
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
