# netdisk-fast-download

[![Java CI with Maven](https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml/badge.svg)](https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml)
[![jdk](https://img.shields.io/badge/jdk-%3E%3D17-blue)](https://www.oracle.com/cn/java/technologies/downloads/)
[![vert.x](https://img.shields.io/badge/vert.x-4.4.1-blue)](https://vertx-china.github.io/)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/qaiu/netdisk-fast-download)](https://github.com/qaiu/netdisk-fast-download/releases/tag/0.1.5-releases)

## 网盘支持情况:
`网盘名称(网盘标识):`

- [蓝奏云 (lz)](https://pc.woozooo.com/)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [奶牛快传 (cow)](https://cowtransfer.com/)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [移动云空间 (ec)](https://www.ecpan.cn/web)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [UC网盘 (uc)](https://fast.uc.cn/)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [小飞机网盘 (fj)](https://www.feijipan.com/)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [亿方云 (fc)](https://www.fangcloud.com/)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [123云盘 (ye)](https://www.123pan.com/)
  - [ ]  登录, 上传, 下载, 分享
  - [X]  直链解析
- [文叔叔 (ws) 开发中](https://www.wenshushu.cn/)
- [夸克网盘 (qk) 开发中](https://pan.quark.cn/)

**TODO:**
  - 登录接口, 文件上传/下载/分享后端接口
  - 短地址服务
  - 前端界面(正则规划)

**技术栈:**
Jdk17+Vert.x4.4.1
Core模块集成Vert.x实现类似spring的注解式路由API

API接口

```
网盘标识参考上面网盘支持情况, 括号内是可选内容: 表示当带有分享密码时需要加上密码参数 
parser接口可以直接解析分享链接: 加密分享需要加上参数pwd=密码; 
其他接口在分享Key后面加上@密码;

1. 解析并自动302跳转 : 
    http(s)://your_host/parser?url=分享链接(&pwd=xxx)
    http(s)://your_host/网盘标识/分享key(@分享密码)
2. 获取解析后的直链--JSON格式
    http(s)://your_host/json/网盘标识/分享key(@分享密码)
3. 特别注意的地方: 
  - 有些网盘的加密分享的密码可以忽略: 如移动云空间,小飞机网盘
  - 移动云空间(ec)使用parser?url= 解析时因为分享链接比较特殊(链接带有参数且含有#符号)所以要么对#进行转义%23要么直接去掉# 或者URL直接是主机名+'/'跟一个data参数
  比如 http://your_host/parser?url=https://www.ecpan.cn/web//yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=81027a5c99af5b11ca004966c945cce6W9Bf2&isShare=1
      http://your_host/parser?url=https://www.ecpan.cn/web/%23/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=81027a5c99af5b11ca004966c945cce6W9Bf2&isShare=1
      http://your_host/parser?url=https://www.ecpan.cn/&data=81027a5c99af5b11ca004966c945cce6W9Bf2&isShare=1
```

IDEA HttpClient示例:

```
# 解析并重定向到直链
### 蓝奏云普通分享
# @no-redirect
GET http://127.0.0.1:6400/parser?url=https://lanzoux.com/ia2cntg
### 奶牛快传普通分享
# @no-redirect
GET http://127.0.0.1:6400/parser?url=https://cowtransfer.com/s/9a644fe3e3a748
### 360亿方云加密分享
# @no-redirect
GET http://127.0.0.1:6400/parser?url=https://v2.fangcloud.com/sharing/e5079007dc31226096628870c7&pwd=QAIU

# Rest请求自动302跳转(只提供共享文件Id):
### 蓝奏云普通分享
# @no-redirect
GET http://127.0.0.1:6400/lz/ia2cntg
### 奶牛快传普通分享
# @no-redirect
GET http://127.0.0.1:6400/cow/9a644fe3e3a748
### 360亿方云加密分享
GET http://127.0.0.1:6400/json/fc/e5079007dc31226096628870c7@QAIU


# 解析返回json直链
### 蓝奏云普通分享
GET http://127.0.0.1:6400/json/lz/ia2cntg
### 奶牛快传普通分享
GET http://127.0.0.1:6400/json/cow/9a644fe3e3a748
### 360亿方云加密分享
GET http://127.0.0.1:6400/json/fc/e5079007dc31226096628870c7@QAIU


```

# 网盘对比


| 网盘名称       | 可直接下载分享    | 加密分享     | 初始网盘空间    | 单文件大小限制 | 登录接口 |
|------------|------------|----------|-----------|---------|------|
| 蓝奏云        | √          | √        | 不限空间      | 100M    | TODO |
| 奶牛快传       | √          | X        | 10G       | 不限大小    | TODO |
| 移动云空间      | √          | √(密码可忽略) | 5G(个人)    | 不限大小    | TODO |
| UC网盘       | √          | √        | 10G       | 不限大小    | TODO |
| 小飞机网盘      | √          | √(密码可忽略) | 10G       | 不限大小    | TODO |
| 360亿方云     | √(注意有流量限制) | √(密码可忽略) | 100G(须实名) | 不限大小    | TODO |
| 123云盘      | √          | √        | 2T        | 100G    | TODO |
| 文叔叔(TODO)  | √(注意有时间限制) | √        | 10G       |  5GB    | TODO |
| 夸克网盘(TODO) | 需要登录       | √        | 10G       | 不限大小    | TODO |

# 打包部署

## 开发和打包

```shell
# 环境要求: Jdk17 + maven; 
mvn clean 
mvn package

```
打包好的文件位于 web-service/target/netdisk-fast-download-x.x.x-bin.zip
## Linux服务部署
> 注意: netdisk-fast-download.service中的ExecStart的路径改为实际路径
```shell
cd ~
wget -O netdisk-fast-download-0.1.5-bin.zip  https://github.com/qaiu/netdisk-fast-download/releases/download/0.1.5-releases/netdisk-fast-download-0.1.5-bin.zip
unzip netdisk-fast-download-*-bin.zip 
cd netdisk-fast-download-*-bin
bash service-install.sh
```
服务相关命令:  
1、查看服务状态  
systemctl status netdisk-fast-download.service

2、控制服务  
启动服务  
systemctl start netdisk-fast-download.service

重启服务  
systemctl restart netdisk-fast-download.service

停止服务  
systemctl stop netdisk-fast-download.service

开机启动服务  
systemctl enable netdisk-fast-download.servic

停止开机启动  
systemctl disable netdisk-fast-download.servic

## Windows服务部署
1. 下载并解压releases版本netdisk-fast-download-0.1.5-bin.zip
2. 进入netdisk-fast-download-0.1.5-bin目录
3. 使用管理员权限运行nfd-service-install.bat
如果不想使用服务运行可以直接运行run.bat

## Docker部署
TODO
