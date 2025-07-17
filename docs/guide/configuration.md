# 配置说明

本文档详细介绍 Netdisk Fast Download 的各项配置选项。

## 配置文件位置

配置文件位于 `resources` 目录下：

```
resources/
├── app-dev.yml          # 主要应用配置
├── server-proxy.yml     # 代理服务配置
└── http-proxy.yml       # HTTP代理配置
```

## 主配置文件 (app-dev.yml)

### 基本应用配置

```yaml
app:
  name: "netdisk-fast-download"  # 应用名称
  port: 6400                     # 服务端口
  host: "0.0.0.0"               # 监听地址
  
server:
  compressionSupported: true     # 启用压缩
  decompressionSupported: true   # 启用解压缩
```

### 缓存配置

```yaml
cache:
  expireTime: 1800              # 缓存过期时间（秒）
  maxSize: 1000                 # 最大缓存条目数
  cleanupInterval: 300          # 清理间隔（秒）
```

### 数据库配置

```yaml
database:
  enabled: true                 # 启用数据库
  path: "./db/cache.db"        # 数据库文件路径
  maxConnections: 10           # 最大连接数
```

### 日志配置

```yaml
logging:
  level: "INFO"                # 日志级别: DEBUG, INFO, WARN, ERROR
  path: "./logs"               # 日志文件路径
  maxFileSize: "10MB"          # 单个日志文件最大大小
  maxFiles: 5                  # 保留的日志文件数量
```

### 网盘特定配置

```yaml
netdisk:
  lanzou:
    timeout: 30000             # 蓝奏云请求超时时间（毫秒）
    retryCount: 3              # 重试次数
  
  feijipan:
    timeout: 45000             # 小飞机网盘超时时间
    retryCount: 2
    
  cowtransfer:
    referer: "https://cowtransfer.com/"  # 奶牛快传需要的Referer头
```

## 代理配置 (server-proxy.yml)

### HTTP代理配置

```yaml
proxy:
  - panTypes: ["pgd", "pdb", "pod"]  # 需要代理的网盘类型
    type: "http"                     # 代理类型: http, socks4, socks5
    host: "127.0.0.1"               # 代理服务器地址
    port: 7890                       # 代理端口
    username: ""                     # 代理用户名（可选）
    password: ""                     # 代理密码（可选）
    
  - panTypes: ["fj"]                 # 小飞机网盘专用代理
    type: "http"
    host: "proxy.example.com"
    port: 8080
```

### SOCKS代理配置

```yaml
proxy:
  - panTypes: ["pgd", "pod"]
    type: "socks5"
    host: "127.0.0.1"
    port: 1080
    username: "user"
    password: "pass"
```

## 前端代理配置 (http-proxy.yml)

用于配置前端静态文件代理：

```yaml
frontend:
  enabled: true                      # 启用前端服务
  port: 8080                        # 前端服务端口
  staticPath: "./webroot"           # 静态文件路径
  
proxy:
  api:
    target: "http://localhost:6400" # API代理目标
    pathRewrite:
      "^/api": ""                   # 路径重写规则
```

## 环境变量配置

可以通过环境变量覆盖配置文件中的设置：

```bash
# 应用端口
export APP_PORT=6401

# 缓存过期时间
export CACHE_EXPIRE_TIME=3600

# 日志级别
export LOG_LEVEL=DEBUG

# 数据库路径
export DB_PATH="/data/cache.db"
```

## Docker 环境变量

在 Docker 中使用环境变量：

```bash
docker run -d \
  -e APP_PORT=6401 \
  -e CACHE_EXPIRE_TIME=3600 \
  -e LOG_LEVEL=INFO \
  netdisk-fast-download
```

## 配置文件示例

### 生产环境配置示例

```yaml
# app-dev.yml
app:
  name: "netdisk-fast-download"
  port: 6400
  host: "0.0.0.0"

cache:
  expireTime: 3600               # 生产环境延长缓存时间
  maxSize: 5000                  # 增加缓存大小
  
logging:
  level: "WARN"                  # 生产环境减少日志
  path: "/var/log/netdisk"
  
database:
  path: "/data/cache.db"         # 持久化存储路径

# 代理配置（如果需要）
proxy:
  - panTypes: ["pgd", "pod", "pdp"]
    type: "http"
    host: "proxy.internal.com"
    port: 3128
```

### 开发环境配置示例

```yaml
# app-dev.yml
app:
  name: "netdisk-fast-download-dev"
  port: 6400
  host: "127.0.0.1"             # 仅本地访问

cache:
  expireTime: 300                # 开发环境短缓存时间
  maxSize: 100
  
logging:
  level: "DEBUG"                 # 开发环境详细日志
  
database:
  path: "./dev-cache.db"         # 开发数据库
```

## 配置验证

启动时系统会验证配置文件：

```bash
# 检查配置文件语法
java -jar netdisk-fast-download.jar --validate-config

# 输出当前配置
java -jar netdisk-fast-download.jar --show-config
```

## 动态配置重载

部分配置支持动态重载（无需重启服务）：

```bash
# 发送 HUP 信号重载配置
kill -HUP $(pgrep -f netdisk-fast-download)
```

支持动态重载的配置项：
- 日志级别
- 缓存配置
- 代理配置

## 常见配置问题

### 1. 端口冲突
```yaml
# 修改端口
app:
  port: 6401
```

### 2. 内存不足
```yaml
# 减少缓存大小
cache:
  maxSize: 500
```

### 3. 网络超时
```yaml
# 增加超时时间
netdisk:
  timeout: 60000
```

### 4. 代理不工作
```yaml
# 检查代理配置
proxy:
  - panTypes: ["pgd"]
    type: "http"
    host: "correct-proxy-host"
    port: 8080
```

## 性能调优配置

### 高并发配置
```yaml
app:
  workerThreads: 20              # 增加工作线程数
  
cache:
  maxSize: 10000                 # 增加缓存容量
  expireTime: 7200               # 延长缓存时间

database:
  maxConnections: 50             # 增加数据库连接数
```

### 低资源配置
```yaml
app:
  workerThreads: 2               # 减少线程数
  
cache:
  maxSize: 200                   # 减少缓存
  expireTime: 600                # 缩短缓存时间

logging:
  level: "ERROR"                 # 减少日志输出
```

::: warning 注意事项
- 修改配置后需要重启服务才能生效（除了支持动态重载的配置）
- 代理配置错误可能导致某些网盘无法解析
- 生产环境建议关闭DEBUG日志以提高性能
:::