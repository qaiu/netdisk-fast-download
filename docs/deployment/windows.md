# Windows 系统部署

Windows 系统部署适合开发环境和Windows服务器环境，提供图形化管理界面。

## 系统要求

### 支持的 Windows 版本
- **Windows 10** (1809+)
- **Windows 11**
- **Windows Server 2019**
- **Windows Server 2022**

### 硬件要求
- **CPU**: 1核心以上
- **内存**: 2GB 以上
- **存储**: 10GB 可用空间
- **网络**: 可选公网连接

## 环境准备

### 1. 安装 Java 17

#### 下载 Oracle JDK 17
1. 访问 [Oracle官网](https://www.oracle.com/java/technologies/downloads/#java17)
2. 下载 Windows x64 安装包
3. 运行安装程序，按默认选项安装

#### 或下载 OpenJDK 17
1. 访问 [Adoptium](https://adoptium.net/)
2. 选择 JDK 17 和 Windows x64
3. 下载 `.msi` 安装包并安装

#### 验证 Java 安装
打开命令提示符（cmd）或 PowerShell：
```cmd
java -version
```

如果显示版本信息说明安装成功。

### 2. 配置环境变量（如果需要）

如果 `java` 命令无法识别：

1. 右键 **此电脑** → **属性**
2. 点击 **高级系统设置**
3. 点击 **环境变量**
4. 在系统变量中添加：
   - 变量名: `JAVA_HOME`
   - 变量值: Java安装路径（如：`C:\Program Files\Java\jdk-17`）
5. 编辑 `Path` 变量，添加: `%JAVA_HOME%\bin`

## 应用部署

### 方法1: 手动部署

#### 1. 下载应用文件

1. 访问 [GitHub Releases](https://github.com/qaiu/netdisk-fast-download/releases)
2. 下载最新的 `netdisk-fast-download-bin.zip`
3. 解压到目标目录（如：`C:\netdisk-fast-download\`）

#### 2. 创建启动脚本

创建 `start.bat` 文件：
```batch
@echo off
title Netdisk Fast Download
echo Starting Netdisk Fast Download...

cd /d "%~dp0"

REM Java 参数配置
set JAVA_OPTS=-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.awt.headless=true

REM 启动应用
java %JAVA_OPTS% -jar netdisk-fast-download.jar

pause
```

#### 3. 运行应用

双击 `start.bat` 启动应用，或在命令行中运行：
```cmd
cd C:\netdisk-fast-download
start.bat
```

### 方法2: Windows 服务部署

#### 1. 下载并解压应用

同上述手动部署的步骤1。

#### 2. 安装 Windows 服务

应用包中包含了服务安装脚本：

1. 以 **管理员身份** 运行命令提示符
2. 进入应用目录：
   ```cmd
   cd C:\netdisk-fast-download\bin
   ```
3. 运行服务安装脚本：
   ```cmd
   nfd-service-install.bat
   ```

#### 3. 配置服务参数

编辑 `nfd-service-template.xml` 文件，修改 Java 路径和参数：

```xml
<configuration>
  <id>netdisk-fast-download</id>
  <name>Netdisk Fast Download</name>
  <description>网盘分享链接云解析服务</description>
  
  <executable>java</executable>
  <arguments>-Xms512m -Xmx1024m -XX:+UseG1GC -jar netdisk-fast-download.jar</arguments>
  
  <logmode>rotate</logmode>
  <logpath>logs</logpath>
  
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="20 sec"/>
  <onfailure action="none"/>
</configuration>
```

#### 4. 管理 Windows 服务

##### 通过服务管理器
1. 按 `Win + R`，输入 `services.msc`
2. 找到 "Netdisk Fast Download" 服务
3. 右键选择启动/停止/重启

##### 通过命令行
```cmd
REM 启动服务
net start netdisk-fast-download

REM 停止服务
net stop netdisk-fast-download

REM 重启服务
net stop netdisk-fast-download && net start netdisk-fast-download
```

##### 通过 PowerShell
```powershell
# 启动服务
Start-Service -Name "netdisk-fast-download"

# 停止服务
Stop-Service -Name "netdisk-fast-download"

# 重启服务
Restart-Service -Name "netdisk-fast-download"

# 查看服务状态
Get-Service -Name "netdisk-fast-download"
```

## 配置管理

### 配置文件位置
```
C:\netdisk-fast-download\
├── netdisk-fast-download.jar
├── resources\
│   ├── app-dev.yml
│   ├── server-proxy.yml
│   └── http-proxy.yml
├── logs\
├── db\
└── bin\
    ├── start.bat
    ├── nfd-service-install.bat
    └── nfd-service-template.xml
```

### 编辑配置文件

使用记事本或其他文本编辑器编辑配置：

```yaml
# resources\app-dev.yml
app:
  name: "netdisk-fast-download"
  port: 6400
  host: "0.0.0.0"
  
cache:
  expireTime: 1800
```

修改配置后需要重启服务。

## 防火墙配置

### Windows Defender 防火墙

1. 打开 **Windows Defender 防火墙**
2. 点击 **允许应用或功能通过防火墙**
3. 添加 Java 应用到允许列表
4. 或者添加端口规则：
   - 端口: 6400
   - 协议: TCP
   - 方向: 入站

### 通过 PowerShell 配置
```powershell
# 允许端口 6400
New-NetFirewallRule -DisplayName "Netdisk Fast Download" -Direction Inbound -Protocol TCP -LocalPort 6400 -Action Allow
```

## IIS 反向代理（可选）

如果需要通过 IIS 进行反向代理：

### 1. 安装 IIS 和 ARR 模块

1. 启用 IIS 功能
2. 下载并安装 [Application Request Routing](https://www.iis.net/downloads/microsoft/application-request-routing)
3. 下载并安装 [URL Rewrite](https://www.iis.net/downloads/microsoft/url-rewrite)

### 2. 配置反向代理

在 IIS 管理器中：

1. 创建新网站或选择默认网站
2. 选择 **URL Rewrite**
3. 添加规则：

```xml
<rule name="ReverseProxyInboundRule1" stopProcessing="true">
  <match url="(.*)" />
  <action type="Rewrite" url="http://localhost:6400/{R:1}" />
</rule>
```

## 性能优化

### 1. JVM 参数优化

编辑启动脚本中的 `JAVA_OPTS`：

```batch
REM 针对 2GB 内存的服务器
set JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication

REM 针对 4GB 内存的服务器
set JAVA_OPTS=-Xms2g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication
```

### 2. Windows 性能优化

```powershell
# 设置 Windows 为高性能模式
powercfg -setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c

# 禁用 Windows 更新自动重启
reg add "HKLM\SOFTWARE\Microsoft\WindowsUpdate\UX\Settings" /v UxOption /t REG_DWORD /d 1 /f
```

## 监控和日志

### 1. 查看应用日志

日志文件位置：`C:\netdisk-fast-download\logs\app.log`

```cmd
REM 查看日志文件
type C:\netdisk-fast-download\logs\app.log

REM 实时查看日志（需要安装 Git Bash 或使用 PowerShell）
Get-Content C:\netdisk-fast-download\logs\app.log -Wait -Tail 50
```

### 2. Windows 事件日志

查看 Windows 服务相关日志：

1. 打开 **事件查看器**
2. 导航到 **Windows 日志** → **系统**
3. 筛选事件源为 netdisk-fast-download

### 3. 性能监控

使用 **任务管理器** 或 **性能监视器** 监控：
- CPU 使用率
- 内存使用率
- 网络活动

## 备份与恢复

### 自动备份脚本

创建 `backup.bat` 脚本：

```batch
@echo off
set DATE=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set DATE=%DATE: =0%
set BACKUP_DIR=C:\backup
set APP_DIR=C:\netdisk-fast-download

echo Creating backup for %DATE%...

REM 停止服务
net stop netdisk-fast-download

REM 创建备份目录
if not exist %BACKUP_DIR% mkdir %BACKUP_DIR%

REM 创建备份
7z a "%BACKUP_DIR%\netdisk-backup-%DATE%.zip" "%APP_DIR%\*" -xr!logs\*

REM 启动服务
net start netdisk-fast-download

echo Backup completed: netdisk-backup-%DATE%.zip
```

### 定时备份

使用 **任务计划程序** 设置定时备份：

1. 打开 **任务计划程序**
2. 创建基本任务
3. 设置触发器（如每天执行）
4. 设置操作运行 `backup.bat`

## 故障排除

### 常见问题

#### 1. 端口被占用
```cmd
REM 查看端口占用
netstat -ano | findstr :6400

REM 结束占用进程
taskkill /PID [PID号] /F
```

#### 2. Java 内存不足
```cmd
REM 检查系统内存
systeminfo | findstr "内存"

REM 调整 JVM 参数
set JAVA_OPTS=-Xms256m -Xmx512m
```

#### 3. 服务无法启动
- 检查 Java 环境是否正确
- 验证配置文件语法
- 查看 Windows 事件日志
- 确认端口没有被占用

#### 4. 访问权限问题
```cmd
REM 以管理员身份运行服务安装
runas /user:Administrator nfd-service-install.bat
```

### 诊断命令

```cmd
REM 检查 Java 版本
java -version

REM 检查网络连接
telnet localhost 6400

REM 检查服务状态
sc query netdisk-fast-download

REM 查看进程
tasklist | findstr java
```

## 卸载应用

### 卸载 Windows 服务

1. 停止服务：
   ```cmd
   net stop netdisk-fast-download
   ```

2. 删除服务：
   ```cmd
   sc delete netdisk-fast-download
   ```

3. 删除应用文件：
   ```cmd
   rmdir /s C:\netdisk-fast-download
   ```

### 清理注册表（可选）

如果需要完全清理：

1. 打开 **注册表编辑器** (regedit)
2. 导航到 `HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services`
3. 删除 netdisk-fast-download 相关项

::: warning 注意事项
- Windows 环境下建议使用服务方式部署以确保稳定性
- 定期检查 Windows 更新，某些更新可能影响 Java 环境
- 生产环境建议配置自动备份和监控
- 注意 Windows 防火墙和杀毒软件的影响
:::