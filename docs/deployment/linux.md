# Linux 系统部署

Linux 系统是推荐的生产环境部署平台，提供稳定性和性能保障。

## 系统要求

### 支持的系统版本
- **Ubuntu**: 18.04+
- **CentOS**: 7+
- **Debian**: 9+
- **RHEL**: 7+
- **其他**: 支持 systemd 的 Linux 发行版

### 硬件要求
- **CPU**: 1核心以上
- **内存**: 1GB 以上
- **存储**: 5GB 可用空间
- **网络**: 公网IP（可选）

## 环境准备

### 1. 安装 Java 17

#### Ubuntu/Debian
```bash
# 更新包索引
sudo apt update

# 安装 OpenJDK 17
sudo apt install openjdk-17-jdk

# 验证安装
java -version
```

#### CentOS/RHEL
```bash
# 安装 OpenJDK 17
sudo yum install java-17-openjdk java-17-openjdk-devel

# 验证安装
java -version
```

#### 手动安装（推荐使用阿里 Dragonwell）
```bash
# 下载阿里 Dragonwell JDK 17
wget https://lz.qaiu.top/ec/6ebc9f2e0bbd53b4c4d5b11013f40a80NHvcYU -O dragonwell-17-linux-x64.tar.gz

# 解压到 /usr/local/java
sudo mkdir -p /usr/local/java
sudo tar -xzf dragonwell-17-linux-x64.tar.gz -C /usr/local/java

# 设置环境变量
echo 'export JAVA_HOME=/usr/local/java/dragonwell-17' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### 2. 创建运行用户
```bash
# 创建专用用户
sudo useradd -r -s /bin/false netdisk

# 创建应用目录
sudo mkdir -p /opt/netdisk-fast-download
sudo chown netdisk:netdisk /opt/netdisk-fast-download
```

## 快速部署

### 自动安装脚本
```bash
# 下载并运行安装脚本
wget -O install.sh https://raw.githubusercontent.com/qaiu/netdisk-fast-download/main/scripts/install.sh
chmod +x install.sh
sudo ./install.sh
```

### 手动部署步骤

#### 1. 下载应用
```bash
# 进入应用目录
cd /opt/netdisk-fast-download

# 下载最新版本
sudo wget -O netdisk-fast-download.zip https://github.com/qaiu/netdisk-fast-download/releases/latest/download/netdisk-fast-download-bin.zip

# 解压
sudo unzip netdisk-fast-download.zip

# 设置权限
sudo chown -R netdisk:netdisk /opt/netdisk-fast-download
```

#### 2. 创建 systemd 服务
```bash
# 创建服务文件
sudo tee /etc/systemd/system/netdisk-fast-download.service > /dev/null <<EOF
[Unit]
Description=Netdisk Fast Download Service
After=network.target

[Service]
Type=simple
User=netdisk
Group=netdisk
WorkingDirectory=/opt/netdisk-fast-download
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar netdisk-fast-download.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# 重载 systemd 配置
sudo systemctl daemon-reload

# 启用服务
sudo systemctl enable netdisk-fast-download

# 启动服务
sudo systemctl start netdisk-fast-download
```

#### 3. 配置防火墙
```bash
# Ubuntu/Debian (ufw)
sudo ufw allow 6400/tcp
sudo ufw reload

# CentOS/RHEL (firewalld)
sudo firewall-cmd --permanent --add-port=6400/tcp
sudo firewall-cmd --reload

# CentOS 6/RHEL 6 (iptables)
sudo iptables -A INPUT -p tcp --dport 6400 -j ACCEPT
sudo service iptables save
```

## 服务管理

### systemctl 命令
```bash
# 查看服务状态
sudo systemctl status netdisk-fast-download

# 启动服务
sudo systemctl start netdisk-fast-download

# 停止服务
sudo systemctl stop netdisk-fast-download

# 重启服务
sudo systemctl restart netdisk-fast-download

# 查看日志
sudo journalctl -u netdisk-fast-download -f

# 开机自启
sudo systemctl enable netdisk-fast-download

# 禁用开机自启
sudo systemctl disable netdisk-fast-download
```

### 传统 init.d 脚本（CentOS 6等老系统）
```bash
# 创建启动脚本
sudo tee /etc/init.d/netdisk-fast-download > /dev/null <<'EOF'
#!/bin/bash
# netdisk-fast-download        Netdisk Fast Download Service
# chkconfig: 35 80 20
# description: Netdisk Fast Download Service

. /etc/rc.d/init.d/functions

USER="netdisk"
DAEMON="netdisk-fast-download"
ROOT_DIR=/opt/netdisk-fast-download

SERVER="$ROOT_DIR/netdisk-fast-download.jar"
LOCK_FILE="/var/lock/subsys/netdisk-fast-download"

start() {
    if [ -f $LOCK_FILE ]; then
        echo "$DAEMON is locked."
        return 1
    fi
    
    echo -n $"Shutting down $DAEMON: "
    pid=`ps -aefw | grep "$DAEMON" | grep -v " grep " | awk '{print $2}'`
    kill -9 $pid > /dev/null 2>&1
    [ $? -eq 0 ] && echo "OK" || echo "FAILED"

    echo -n "Starting $DAEMON: "
    daemon --user="$USER" --pidfile="$LOCK_FILE" $SERVER
    RETVAL=$?
    echo
    [ $RETVAL -eq 0 ] && touch $LOCK_FILE
    return $RETVAL
}

stop() {
    echo -n $"Shutting down $DAEMON: "
    pid=`ps -aefw | grep "$DAEMON" | grep -v " grep " | awk '{print $2}'`
    kill -9 $pid > /dev/null 2>&1
    [ $? -eq 0 ] && echo "OK" || echo "FAILED"
    rm -f $LOCK_FILE
}

restart() {
    stop
    start
}

status() {
    if [ -f $LOCK_FILE ]; then
        echo "$DAEMON is running."
    else
        echo "$DAEMON is stopped."
    fi
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    restart)
        restart
        ;;
    *)
        echo "Usage: {start|stop|status|restart}"
        exit 1
        ;;
esac

exit $?
EOF

# 设置权限
sudo chmod +x /etc/init.d/netdisk-fast-download

# 添加到开机启动
sudo chkconfig --add netdisk-fast-download
sudo chkconfig netdisk-fast-download on

# 启动服务
sudo service netdisk-fast-download start
```

## 配置文件管理

### 配置文件位置
```bash
/opt/netdisk-fast-download/
├── netdisk-fast-download.jar
├── resources/
│   ├── app-dev.yml
│   ├── server-proxy.yml
│   └── http-proxy.yml
├── logs/
└── db/
```

### 修改配置
```bash
# 编辑主配置文件
sudo nano /opt/netdisk-fast-download/resources/app-dev.yml

# 重启服务使配置生效
sudo systemctl restart netdisk-fast-download
```

## 日志管理

### 查看日志
```bash
# 查看应用日志
sudo tail -f /opt/netdisk-fast-download/logs/app.log

# 查看系统日志
sudo journalctl -u netdisk-fast-download -f

# 查看错误日志
sudo journalctl -u netdisk-fast-download --since today | grep ERROR
```

### 日志轮转配置
```bash
# 创建 logrotate 配置
sudo tee /etc/logrotate.d/netdisk-fast-download > /dev/null <<EOF
/opt/netdisk-fast-download/logs/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    copytruncate
}
EOF
```

## 性能优化

### JVM 参数调优
```bash
# 编辑服务文件
sudo systemctl edit netdisk-fast-download

# 添加以下内容
[Service]
ExecStart=
ExecStart=/usr/bin/java \
    -Xms1g \
    -Xmx2g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseCompressedOops \
    -XX:+OptimizeStringConcat \
    -Djava.awt.headless=true \
    -jar /opt/netdisk-fast-download/netdisk-fast-download.jar

# 重载配置
sudo systemctl daemon-reload
sudo systemctl restart netdisk-fast-download
```

### 系统参数优化
```bash
# 增加文件描述符限制
echo '* soft nofile 65536' | sudo tee -a /etc/security/limits.conf
echo '* hard nofile 65536' | sudo tee -a /etc/security/limits.conf

# 优化网络参数
sudo tee -a /etc/sysctl.conf > /dev/null <<EOF
net.core.somaxconn = 1024
net.core.netdev_max_backlog = 5000
net.ipv4.tcp_max_syn_backlog = 1024
EOF

sudo sysctl -p
```

## 反向代理配置

### Nginx 配置
```bash
# 安装 Nginx
sudo apt install nginx  # Ubuntu/Debian
sudo yum install nginx  # CentOS/RHEL

# 创建配置文件
sudo tee /etc/nginx/sites-available/netdisk > /dev/null <<EOF
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:6400;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
        
        # 缓存控制
        proxy_cache_bypass \$http_pragma;
        proxy_cache_revalidate on;
    }
}
EOF

# 启用站点
sudo ln -s /etc/nginx/sites-available/netdisk /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

## 备份与恢复

### 自动备份脚本
```bash
#!/bin/bash
# /opt/backup/backup.sh

APP_DIR="/opt/netdisk-fast-download"
BACKUP_DIR="/opt/backup"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/netdisk-backup-$DATE.tar.gz"

# 停止服务
sudo systemctl stop netdisk-fast-download

# 创建备份
sudo tar -czf $BACKUP_FILE \
    --exclude="$APP_DIR/logs/*" \
    $APP_DIR

# 启动服务
sudo systemctl start netdisk-fast-download

# 清理旧备份（保留7天）
find $BACKUP_DIR -name "netdisk-backup-*.tar.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_FILE"
```

### 定时备份
```bash
# 添加到 crontab
sudo crontab -e

# 每天凌晨2点备份
0 2 * * * /opt/backup/backup.sh
```

## 故障排除

### 常见问题诊断
```bash
# 检查端口占用
sudo netstat -tulpn | grep 6400

# 检查进程状态
ps aux | grep netdisk-fast-download

# 检查磁盘空间
df -h

# 检查内存使用
free -h

# 检查系统负载
top
```

### 重置服务
```bash
# 完全重置
sudo systemctl stop netdisk-fast-download
sudo rm -rf /opt/netdisk-fast-download/db/*
sudo rm -rf /opt/netdisk-fast-download/logs/*
sudo systemctl start netdisk-fast-download
```

::: tip 提示
- 生产环境建议配置监控和告警系统
- 定期检查日志文件大小，避免磁盘空间不足
- 建议使用反向代理和 SSL 证书保护服务
:::