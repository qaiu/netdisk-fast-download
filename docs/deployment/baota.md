# 宝塔面板部署

宝塔面板是国内流行的服务器管理面板，提供可视化的部署和管理体验，非常适合新手用户。

## 环境要求

### 服务器要求
- **操作系统**: CentOS 7+, Ubuntu 16+, Debian 9+
- **内存**: 512MB 以上（推荐1GB+）
- **磁盘**: 10GB 以上可用空间
- **架构**: x86_64

### 宝塔面板版本
- **免费版**: 7.7.0+
- **专业版**: 7.7.0+（推荐）

## 宝塔面板安装

### CentOS 安装命令
```bash
yum install -y wget && wget -O install.sh http://download.bt.cn/install/install_6.0.sh && sh install.sh ed8484bec
```

### Ubuntu/Debian 安装命令
```bash
wget -O install.sh http://download.bt.cn/install/install-ubuntu_6.0.sh && sudo bash install.sh ed8484bec
```

安装完成后记录面板地址、用户名和密码。

## 环境配置

### 1. 安装 Java 环境

在宝塔面板中安装 Java：

1. 进入 **软件商店**
2. 搜索 **"Java"**
3. 安装 **Java项目运行环境** 或 **Tomcat**
4. 选择 **Java 17** 版本

#### 手动安装 Java 17（推荐）

通过 **终端** 或 **SSH** 安装：

```bash
# CentOS
yum install -y java-17-openjdk java-17-openjdk-devel

# Ubuntu/Debian  
apt update && apt install -y openjdk-17-jdk

# 验证安装
java -version
```

### 2. 创建网站

1. 进入 **网站** 管理
2. 点击 **添加站点**
3. 配置如下：
   - **域名**: 你的域名或IP
   - **根目录**: `/www/wwwroot/netdisk`
   - **PHP版本**: 纯静态
   - **数据库**: 不创建

### 3. 配置反向代理

1. 进入刚创建的网站 **设置**
2. 点击 **反向代理**
3. 添加反向代理：
   - **代理名称**: netdisk-api
   - **目标URL**: `http://127.0.0.1:6400`
   - **发送域名**: `$host`
   - **缓存**: 不启用

## 应用部署

### 1. 下载应用文件

通过 **文件管理** 或 **终端**：

```bash
# 进入网站目录
cd /www/wwwroot/netdisk

# 下载最新版本
wget -O netdisk-fast-download.zip https://github.com/qaiu/netdisk-fast-download/releases/latest/download/netdisk-fast-download-bin.zip

# 解压文件
unzip netdisk-fast-download.zip

# 设置权限
chmod +x netdisk-fast-download.jar
```

### 2. 创建启动脚本

创建 `start.sh` 启动脚本：

```bash
#!/bin/bash
# start.sh

APP_DIR="/www/wwwroot/netdisk"
JAR_FILE="$APP_DIR/netdisk-fast-download.jar"
PID_FILE="$APP_DIR/app.pid"
LOG_FILE="$APP_DIR/logs/app.log"

# 创建日志目录
mkdir -p $APP_DIR/logs

# Java 参数
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.awt.headless=true"

# 启动函数
start() {
    if [ -f $PID_FILE ]; then
        echo "Application is already running, PID: $(cat $PID_FILE)"
        return 1
    fi
    
    echo "Starting Netdisk Fast Download..."
    nohup java $JAVA_OPTS -jar $JAR_FILE > $LOG_FILE 2>&1 & echo $! > $PID_FILE
    echo "Application started, PID: $(cat $PID_FILE)"
}

# 停止函数
stop() {
    if [ ! -f $PID_FILE ]; then
        echo "Application is not running"
        return 1
    fi
    
    PID=$(cat $PID_FILE)
    echo "Stopping Netdisk Fast Download (PID: $PID)..."
    kill $PID
    rm -f $PID_FILE
    echo "Application stopped"
}

# 重启函数
restart() {
    stop
    sleep 3
    start
}

# 状态检查
status() {
    if [ -f $PID_FILE ]; then
        PID=$(cat $PID_FILE)
        if ps -p $PID > /dev/null; then
            echo "Application is running, PID: $PID"
        else
            echo "PID file exists but process is not running"
            rm -f $PID_FILE
        fi
    else
        echo "Application is not running"
    fi
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        restart
        ;;
    status)
        status
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
```

设置执行权限：
```bash
chmod +x start.sh
```

### 3. 配置守护进程

使用宝塔的 **进程守护器**：

1. 进入 **软件商店**
2. 安装 **进程守护器**
3. 添加守护进程：
   - **进程名称**: netdisk-fast-download
   - **启动文件**: `/www/wwwroot/netdisk/start.sh start`
   - **执行目录**: `/www/wwwroot/netdisk`
   - **守护进程**: 开启

## SSL 证书配置

### 1. 申请免费证书

1. 进入网站 **设置**
2. 点击 **SSL**
3. 选择 **Let's Encrypt** 免费证书
4. 填写邮箱并申请

### 2. 强制 HTTPS

证书安装完成后：
1. 开启 **强制HTTPS**
2. 开启 **HSTS**
3. 更新反向代理配置

## 配置文件管理

### 通过文件管理器

1. 进入 **文件** 管理
2. 导航到 `/www/wwwroot/netdisk/resources/`
3. 编辑配置文件：
   - `app-dev.yml` - 主配置
   - `server-proxy.yml` - 代理配置

### 主要配置项

```yaml
# app-dev.yml
app:
  name: "netdisk-fast-download"
  port: 6400                    # 确保与反向代理端口一致
  host: "127.0.0.1"            # 仅本地监听

cache:
  expireTime: 1800             # 缓存时间（秒）

# 如果需要代理
proxy:
  - panTypes: pgd,pod,pdp
    type: http
    host: 127.0.0.1
    port: 7890
```

## 性能优化

### 1. 开启页面缓存

在网站设置中：
1. 开启 **页面缓存**
2. 设置缓存时间 30 分钟
3. 排除动态接口：`/json/*`, `/v2/*`

### 2. 开启 Gzip 压缩

1. 进入网站 **设置**
2. 开启 **Gzip压缩**
3. 选择压缩文件类型

### 3. 配置 CDN（可选）

如果使用百度云、阿里云等CDN：
1. 在网站设置中添加 **CDN加速**
2. 配置回源地址
3. 设置缓存规则

## 监控与日志

### 1. 系统监控

宝塔自带系统监控：
1. 查看 **系统状态**
2. 监控 **CPU/内存/磁盘** 使用率
3. 设置 **告警规则**

### 2. 应用日志

查看应用日志：
```bash
# 实时日志
tail -f /www/wwwroot/netdisk/logs/app.log

# 错误日志
grep ERROR /www/wwwroot/netdisk/logs/app.log
```

### 3. 访问日志

在网站设置中：
1. 开启 **访问日志**
2. 定期分析访问情况

## 安全配置

### 1. 防火墙设置

1. 进入 **安全** 管理
2. 配置防火墙规则：
   - 放行 `80` 端口（HTTP）
   - 放行 `443` 端口（HTTPS）
   - 禁止直接访问 `6400` 端口

### 2. IP 访问限制

在网站设置中：
1. 配置 **访问限制**
2. 设置 **IP白名单**（如需要）
3. 启用 **防盗链**

### 3. 文件权限

设置合适的文件权限：
```bash
# 设置应用文件权限
chown -R www:www /www/wwwroot/netdisk
chmod -R 755 /www/wwwroot/netdisk
chmod +x /www/wwwroot/netdisk/start.sh
```

## 备份与恢复

### 1. 自动备份

设置宝塔自动备份：
1. 进入 **计划任务**
2. 添加 **备份网站** 任务
3. 设置备份频率（建议每天）
4. 配置备份保留天数

### 2. 手动备份

创建备份脚本：
```bash
#!/bin/bash
# backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/www/backup"
SOURCE_DIR="/www/wwwroot/netdisk"

mkdir -p $BACKUP_DIR

# 停止应用
./start.sh stop

# 创建备份
tar -czf "$BACKUP_DIR/netdisk-$DATE.tar.gz" \
    --exclude="$SOURCE_DIR/logs/*" \
    $SOURCE_DIR

# 重启应用
./start.sh start

echo "Backup completed: netdisk-$DATE.tar.gz"
```

## 故障排除

### 常见问题

1. **应用无法启动**
   ```bash
   # 检查Java环境
   java -version
   
   # 检查端口占用
   netstat -tulpn | grep 6400
   
   # 查看启动日志
   tail -f logs/app.log
   ```

2. **反向代理无效**
   - 检查代理配置
   - 确认目标端口正确
   - 重启 Nginx

3. **SSL 证书问题**
   - 检查域名解析
   - 验证证书状态
   - 重新申请证书

### 重启服务

通过宝塔面板：
1. 进入 **进程守护器**
2. 重启 netdisk-fast-download 进程

或通过命令行：
```bash
cd /www/wwwroot/netdisk
./start.sh restart
```

## 升级应用

### 升级步骤

1. 备份当前版本
2. 下载新版本文件
3. 停止旧版本
4. 替换应用文件
5. 启动新版本
6. 验证功能正常

### 自动升级脚本

```bash
#!/bin/bash
# upgrade.sh

BACKUP_DIR="/www/backup"
APP_DIR="/www/wwwroot/netdisk"
DATE=$(date +%Y%m%d_%H%M%S)

echo "Starting upgrade process..."

# 备份当前版本
cd $APP_DIR
./start.sh stop
cp netdisk-fast-download.jar "$BACKUP_DIR/netdisk-fast-download-$DATE.jar"

# 下载新版本
wget -O netdisk-fast-download-new.jar https://github.com/qaiu/netdisk-fast-download/releases/latest/download/netdisk-fast-download.jar

# 替换文件
mv netdisk-fast-download.jar netdisk-fast-download-old.jar
mv netdisk-fast-download-new.jar netdisk-fast-download.jar
chmod +x netdisk-fast-download.jar

# 启动服务
./start.sh start

echo "Upgrade completed"
```

::: tip 小贴士
- 建议在低峰期进行部署和升级操作
- 定期查看系统资源使用情况，及时优化配置
- 保持宝塔面板和系统的更新，确保安全性
- 为重要数据配置多重备份策略
:::