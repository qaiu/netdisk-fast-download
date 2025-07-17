# 快速开始

## 环境要求

- **JDK**: 17 或以上版本
- **Maven**: 3.6 或以上版本
- **Node.js**: 18 或以上版本（用于前端构建）

## 获取项目

```bash
# 克隆项目
git clone https://github.com/qaiu/netdisk-fast-download.git

# 进入项目目录
cd netdisk-fast-download
```

## 构建项目

### 1. 构建前端

```bash
cd web-front
yarn install
yarn run build
```

### 2. 构建后端

```bash
# 返回项目根目录
cd ..

# 清理并打包
mvn clean package
```

打包完成后，生成的文件位于 `web-service/target/netdisk-fast-download-bin.zip`

## 运行项目

### 开发模式

```bash
# 启动后端（默认端口 6400）
cd web-service
java -jar target/netdisk-fast-download.jar

# 启动前端开发服务器（另开终端）
cd web-front
yarn serve
```

### 生产模式

解压打包文件并运行：

```bash
unzip netdisk-fast-download-bin.zip
cd netdisk-fast-download
java -jar netdisk-fast-download.jar
```

## 验证安装

服务启动后，你可以通过以下方式验证：

1. 访问 `http://localhost:6400` 查看 Web 界面
2. 测试 API 接口：

```bash
# 测试蓝奏云解析
curl "http://localhost:6400/json/parser?url=https://lanzoux.com/ia2cntg"

# 测试统计接口
curl "http://localhost:6400/v2/statisticsInfo"
```

## 配置文件说明

主要配置文件位于 `resources` 目录：

- `app-dev.yml` - 主要服务配置
- `server-proxy.yml` - 代理服务配置  
- `http-proxy.yml` - HTTP代理配置

### 基本配置示例

```yaml
# app-dev.yml
app:
  name: "netdisk-fast-download"
  port: 6400
  host: "0.0.0.0"
  
cache:
  expireTime: 1800 # 缓存过期时间（秒）
  
proxy:
  - panTypes: pgd,pdb,pod
    type: http
    host: 127.0.0.1
    port: 7890
```

## 下一步

- [查看网盘支持情况](/guide/supported-platforms)
- [了解详细配置](/guide/configuration)
- [学习 API 使用](/api/)
- [部署到生产环境](/deployment/)