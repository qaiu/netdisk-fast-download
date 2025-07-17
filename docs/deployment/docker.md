# Docker 部署

Docker 是推荐的部署方式，提供了环境一致性和便于管理的优势。

## 系统要求

- Docker 20.10+
- Docker Compose（可选）
- 2GB 内存
- 10GB 存储空间

## 快速部署

### 海外服务器部署

```bash
# 创建目录
mkdir -p netdisk-fast-download
cd netdisk-fast-download

# 拉取镜像
docker pull ghcr.io/qaiu/netdisk-fast-download:latest

# 复制配置文件
docker create --name netdisk-fast-download ghcr.io/qaiu/netdisk-fast-download:latest
docker cp netdisk-fast-download:/app/resources ./resources
docker rm netdisk-fast-download

# 启动容器
docker run -d -it \
  --name netdisk-fast-download \
  -p 6401:6401 \
  --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -v ./resources:/app/resources \
  -v ./db:/app/db \
  -v ./logs:/app/logs \
  ghcr.io/qaiu/netdisk-fast-download:latest
```

### 国内服务器部署

```bash
# 创建目录
mkdir -p netdisk-fast-download
cd netdisk-fast-download

# 拉取镜像（使用国内镜像源）
docker pull ghcr.nju.edu.cn/qaiu/netdisk-fast-download:latest

# 复制配置文件
docker create --name netdisk-fast-download ghcr.nju.edu.cn/qaiu/netdisk-fast-download:latest
docker cp netdisk-fast-download:/app/resources ./resources
docker rm netdisk-fast-download

# 启动容器
docker run -d -it \
  --name netdisk-fast-download \
  -p 6401:6401 \
  --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -v ./resources:/app/resources \
  -v ./db:/app/db \
  -v ./logs:/app/logs \
  ghcr.nju.edu.cn/qaiu/netdisk-fast-download:latest
```

## Docker Compose 部署

创建 `docker-compose.yml` 文件：

```yaml
version: '3.8'

services:
  netdisk-fast-download:
    image: ghcr.io/qaiu/netdisk-fast-download:latest
    container_name: netdisk-fast-download
    restart: unless-stopped
    ports:
      - "6401:6401"
    environment:
      - TZ=Asia/Shanghai
    volumes:
      - ./resources:/app/resources
      - ./db:/app/db
      - ./logs:/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:6401/v2/statisticsInfo"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  # 可选：添加 Nginx 反向代理
  nginx:
    image: nginx:alpine
    container_name: netdisk-nginx
    restart: unless-stopped
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - netdisk-fast-download
```

启动服务：

```bash
docker-compose up -d
```

## 容器管理

### 查看容器状态
```bash
docker ps
```

### 查看日志
```bash
# 查看实时日志
docker logs -f netdisk-fast-download

# 查看最近100行日志
docker logs --tail 100 netdisk-fast-download
```

### 进入容器
```bash
docker exec -it netdisk-fast-download /bin/bash
```

### 重启容器
```bash
docker restart netdisk-fast-download
```

### 停止和删除容器
```bash
# 停止容器
docker stop netdisk-fast-download

# 删除容器
docker rm netdisk-fast-download
```

## 容器升级

### 手动升级
```bash
# 停止旧容器
docker stop netdisk-fast-download
docker rm netdisk-fast-download

# 拉取新镜像
docker pull ghcr.io/qaiu/netdisk-fast-download:latest

# 启动新容器（使用之前的启动命令）
docker run -d -it \
  --name netdisk-fast-download \
  -p 6401:6401 \
  --restart unless-stopped \
  -e TZ=Asia/Shanghai \
  -v ./resources:/app/resources \
  -v ./db:/app/db \
  -v ./logs:/app/logs \
  ghcr.io/qaiu/netdisk-fast-download:latest
```

### 自动升级（Watchtower）
```bash
# 安装 Watchtower 自动升级
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  containrrr/watchtower \
  --cleanup \
  --run-once \
  netdisk-fast-download
```

## 配置文件修改

配置文件位于 `./resources` 目录：

```bash
# 编辑主配置文件
nano ./resources/app-dev.yml

# 编辑代理配置
nano ./resources/server-proxy.yml

# 重启容器使配置生效
docker restart netdisk-fast-download
```

## 反向代理配置

### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    location / {
        proxy_pass http://localhost:6401;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 文件上传大小限制
        client_max_body_size 100M;
        
        # 超时设置
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### Caddy 配置示例

```caddyfile
your-domain.com {
    reverse_proxy localhost:6401
}
```

## 数据备份

### 备份数据库和配置
```bash
# 创建备份目录
mkdir -p backup/$(date +%Y%m%d)

# 备份数据库
cp -r ./db backup/$(date +%Y%m%d)/

# 备份配置文件
cp -r ./resources backup/$(date +%Y%m%d)/

# 备份日志
cp -r ./logs backup/$(date +%Y%m%d)/
```

### 恢复数据
```bash
# 停止容器
docker stop netdisk-fast-download

# 恢复数据
cp -r backup/20240101/db ./
cp -r backup/20240101/resources ./

# 启动容器
docker start netdisk-fast-download
```

## 故障排除

### 常见问题

1. **端口冲突**
   ```bash
   # 检查端口占用
   netstat -tulpn | grep 6401
   
   # 修改端口映射
   -p 6402:6401
   ```

2. **容器无法启动**
   ```bash
   # 查看详细错误信息
   docker logs netdisk-fast-download
   
   # 检查配置文件权限
   chmod -R 755 ./resources
   ```

3. **内存不足**
   ```bash
   # 限制容器内存使用
   docker run --memory="1g" ...
   ```

4. **磁盘空间不足**
   ```bash
   # 清理无用镜像
   docker image prune -f
   
   # 清理无用容器
   docker container prune -f
   ```

::: tip 提示
- 建议定期备份数据和配置文件
- 生产环境建议使用 Docker Compose 进行管理
- 可以通过环境变量覆盖配置文件中的部分设置
:::