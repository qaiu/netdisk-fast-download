# netdisk-fast-download
云盘解析服务 (nfd云解析)
预览地址 https://lz.qaiu.top

[![Java CI with Maven](https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml/badge.svg)](https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml)
[![jdk](https://img.shields.io/badge/jdk-%3E%3D17-blue)](https://www.oracle.com/cn/java/technologies/downloads/)
[![vert.x](https://img.shields.io/badge/vert.x-4.4.1-blue)](https://vertx-china.github.io/)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/qaiu/netdisk-fast-download)](https://github.com/qaiu/netdisk-fast-download/releases/tag/0.1.6-releases)

## 项目介绍
网盘直链解析工具能把网盘分享下载链接转化为直链，已支持蓝奏云/奶牛快传/移动云云空间/小飞机盘/亿方云/123云盘等，支持私密分享。


*重要声明：本项目仅供学习参考；请不要将此项目用于任何商业用途，否则可能带来严重的后果。*

## 网盘支持情况:
> 20230905 奶牛云直链做了防盗链，需加入请求头：Referer: https://cowtransfer.com/  
> 20230824 123云盘解析大文件(>100MB)失效，需要登录  
> 20230722 UC网盘解析失效，需要登录  

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
- [UC网盘 (uc)似乎已经失效，需要登录](https://fast.uc.cn/)
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
  - 前端界面(建设中...)

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
    http(s)://your_host/json/parser?url=分享链接(&pwd=xxx)
    http(s)://your_host/json/网盘标识/分享key(@分享密码)
3. 特别注意的地方: 
  - 有些网盘的加密分享的密码可以忽略: 如移动云空间,小飞机网盘
  - 移动云空间(ec)使用parser?url= 解析时因为分享链接比较特殊(链接带有参数且含有#符号)所以要么对#进行转义%23要么直接去掉# 或者URL直接是主机名+'/'跟一个data参数
  比如 http://your_host/parser?url=https://www.ecpan.cn/web//yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=81027a5c99af5b11ca004966c945cce6W9Bf2&isShare=1
      http://your_host/parser?url=https://www.ecpan.cn/web/%23/yunpanProxy?path=%2F%23%2Fdrive%2Foutside&data=81027a5c99af5b11ca004966c945cce6W9Bf2&isShare=1
      http://your_host/parser?url=https://www.ecpan.cn/&data=81027a5c99af5b11ca004966c945cce6W9Bf2&isShare=1
```
json返回数据格式示例:  
```json
{
    "code": 200,
    "msg": "success",
    "success": true,
    "count": 0,
    "data": "https://下载链接",
    "timestamp": 1690733953927
}
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


| 网盘名称       | 可直接下载分享                | 加密分享     | 初始网盘空间    | 单文件大小限制 | 登录接口 |
|------------|------------------------|----------|-----------|---------|------|
| 蓝奏云        | √                      | √        | 不限空间      | 100M    | TODO |
| 奶牛快传       | √                      | X        | 10G       | 不限大小    | TODO |
| 移动云空间      | √                      | √(密码可忽略) | 5G(个人)    | 不限大小    | TODO |
| UC网盘       | 需要登录                      | √        | 10G       | 不限大小    | TODO |
| 小飞机网盘      | √                      | √(密码可忽略) | 10G       | 不限大小    | TODO |
| 360亿方云     | √(试用账号有时间限制企业版需要599续费) | √(密码可忽略) | 100G(须实名) | 不限大小    | TODO |
| 123云盘      | √                      | √        | 2T        | 100G    | TODO |
| 文叔叔(TODO)  | √(注意有时间限制)             | √        | 10G       |  5GB    | TODO |
| 夸克网盘(TODO) | 需要登录                   | √        | 10G       | 不限大小    | TODO |

# 打包部署

## JDK下载（lz.qaiu.top提供直链云解析服务）
- [阿里jdk17(Dragonwell17-windows-x86)](https://lz.qaiu.top/ye/iaKtVv-hbECd)
- [阿里jdk17(Dragonwell17-linux-x86)](https://lz.qaiu.top/ye/iaKtVv-AbECd)
- [阿里jdk17(Dragonwell17-linux-aarch64)](https://lz.qaiu.top/ye/iaKtVv-HbECd)
- [123云盘解析有效性测试用-阿里jdk17(Dragonwell17-linux-aarch64)](https://lz.qaiu.top/json/ye/iaKtVv-HbECd)

## 开发和打包

```shell
# 环境要求: Jdk17 + maven; 
mvn clean 
mvn package

```
打包好的文件位于 web-service/target/netdisk-fast-download-bin.zip
## Linux服务部署
> 注意: netdisk-fast-download.service中的ExecStart的路径改为实际路径
```shell
cd ~
wget -O netdisk-fast-download.zip  https://github.com/qaiu/netdisk-fast-download/releases/download/0.1.7/netdisk-fast-download.zip
unzip netdisk-fast-download-bin.zip 
cd netdisk-fast-download
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
1. 下载并解压releases版本netdisk-fast-download-bin.zip
2. 进入netdisk-fast-download下的bin目录
3. 使用管理员权限运行nfd-service-install.bat
如果不想使用服务运行可以直接运行run.bat
> 注意: 如果jdk环境变量的java版本不是17请修改nfd-service-template.xml中的java命令的路径改为实际路径

## Docker部署
TODO ver 0.1.8
