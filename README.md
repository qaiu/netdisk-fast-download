<p align="center">
  <img src="https://github.com/user-attachments/assets/87401aae-b0b6-4ffb-bbeb-44756404d26f" alt="项目预览图" />
</p>

<p align="center">
<a href="https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml"><img src="https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml/badge.svg?style=flat"></a>
<a href="https://www.oracle.com/cn/java/technologies/downloads"><img src="https://img.shields.io/badge/jdk-%3E%3D17-blue"></a>
<a href="https://vertx-china.github.io"><img src="https://img.shields.io/badge/vert.x-4.5.6-blue?style=flat"></a>
<a href="https://raw.githubusercontent.com/qaiu/netdisk-fast-download/master/LICENSE"><img src="https://img.shields.io/github/license/qaiu/netdisk-fast-download?style=flat"></a>
<a href="https://github.com/qaiu/netdisk-fast-download/releases/"><img src="https://img.shields.io/github/v/release/qaiu/netdisk-fast-download?style=flat"></a>
</p>




# netdisk-fast-download 网盘分享链接云解析服务

netdisk-fast-download网盘直链云解析(nfd云解析)能把网盘分享下载链接转化为直链，支持多款云盘，已支持蓝奏云/蓝奏云优享/奶牛快传/移动云云空间/小飞机盘/亿方云/123云盘/Cloudreve等，支持加密分享，以及部分网盘文件夹分享。  

[预览地址1](https://lz.qaiu.top)
[预览地址2](http://www.722shop.top:6401)

main分支依赖JDK17, 提供了JDK11分支[main-jdk11](https://github.com/qaiu/netdisk-fast-download/tree/main-jdk11)  
**0.1.8及以上版本json接口格式有调整 参考json返回数据格式示例**  
**小飞机解析有IP限制，多数云服务商的大陆IP会被拦截（可以自行配置代理），和本程序无关**  
**注意: 请不要过度依赖lz.qaiu.top预览地址服务，建议本地搭建或者云服务器自行搭建。解析次数过多IP会被部分网盘厂商限制，不推荐做公共解析。**  

## 网盘支持情况:
> 20230905 奶牛云直链做了防盗链，需加入请求头：Referer: https://cowtransfer.com/  
> 20230824 123云盘解析大文件(>100MB)失效，需要登录  
> 20230722 UC网盘解析失效，需要登录  

网盘名称-网盘标识:  

- [蓝奏云-lz](https://pc.woozooo.com/)
- [蓝奏云优享-iz](https://www.ilanzou.com/)
- [奶牛快传-cow](https://cowtransfer.com/)
- [移动云云空间-ec](https://www.ecpan.cn/web)
- [小飞机网盘-fj](https://www.feijipan.com/)
- [亿方云-fc](https://www.fangcloud.com/)
- [123云盘-ye](https://www.123pan.com/)
- ~[115网盘(失效)-p115](https://115.com/)~
- [118网盘(已停服)-p118](https://www.118pan.com/)
- [文叔叔-ws](https://www.wenshushu.cn/)
- [联想乐云-le](https://lecloud.lenovo.com/)
- [QQ邮箱文件中转站-qq](https://mail.qq.com/)
- [城通网盘-ct](https://www.ctfile.com)
- [网易云音乐分享链接-mnes](https://music.163.com)
- [酷狗音乐分享链接-mkgs](https://www.kugou.com)
- [酷我音乐分享链接-mkws](https://kuwo.cn)
- [QQ音乐分享链接-mqqs](https://y.qq.com)
- 咪咕音乐分享链接(开发中)
- [Cloudreve自建网盘-ce](https://github.com/cloudreve/Cloudreve)
- ~[微雨云存储-pvvy](https://www.vyuyun.com/)~
- [超星云盘(需要referer: https://pan-yz.chaoxing.com)-pcx](https://pan-yz.chaoxing.com)
- Google云盘-pgd
- Onedrive-pod
- Dropbox-pdp
- iCloud-pic
### 仅专属版提供
- [移动云盘-p139](https://yun.139.com/)
- [联通云盘-pwo](https://pan.wo.cn/)
- [天翼云盘-p189](https://cloud.189.cn/)

### API接口说明
  your_host指的是您的域名或者IP，实际使用时替换为实际域名或者IP，端口默认6400，可以使用nginx代理来做域名访问。    
  解析方式分为两种类型直接跳转下载文件和获取下载链接,  
每一种都提供了两种接口形式: `通用接口parser?url=`和`网盘标志/分享key拼接的短地址（标志短链）`，所有规则参考示例。
- 通用接口: `/parser?url=分享链接&pwd=密码` 没有分享密码去掉&pwd参数;
- 标志短链: `/d/网盘标识/分享key@密码` 没有分享密码去掉@密码;
- 直链JSON: `/json/网盘标识/分享key@密码`和`/json/parser?url=分享链接&pwd=密码`
- 网盘标识参考上面网盘支持情况
- 当带有分享密码时需要加上密码参数(pwd)
- 移动云云空间,小飞机网盘的加密分享的密码可以忽略
- 移动云空间分享key取分享链接中的data参数,比如`&data=xxx`的参数就是xxx

API规则: 
> 建议使用UrlEncode编码分享链接
```

1. 解析并自动302跳转
    http://your_host/parser?url=分享链接&pwd=xxx
    或者 http://your_host/parser?url=UrlEncode(分享链接)&pwd=xxx  
    http://your_host/d/网盘标识/分享key@分享密码
2. 获取解析后的直链--JSON格式
    http://your_host/json/parser?url=分享链接&pwd=xxx
    http://your_host/json/网盘标识/分享key@分享密码
3. 文件夹解析v0.1.8fixed3新增
    http://your_host/json/getFileList?url=分享链接&pwd=xxx
```
json返回数据格式示例:  
`shareKey`:    全局分享key  
`directLink`:  下载链接  
`cacheHit`:    是否为缓存链接  
`expires`:     缓存到期时间  
```json
{
  "code": 200,
  "msg": "success",
  "success": true,
  "count": 0,
  "data": {
    "shareKey": "lz:xxx",
    "directLink": "下载直链", 
    "cacheHit": true,
    "expires": "2024-09-18 01:48:02",
    "expiration": 1726638482825
  },
  "timestamp": 1726637151902
}
```
2. 分享链接详情接口 /v2/linkInfo?url=分享链接
```json
{
    "code": 200,
    "msg": "success",
    "success": true,
    "count": 0,
    "data": {
        "downLink": "https://lz.qaiu.top/d/fj/xx",
        "apiLink": "https://lz.qaiu.top/json/fj/xx",
        "cacheHitTotal": 5,
        "parserTotal": 2,
        "sumTotal": 7,
        "shareLinkInfo": {
            "shareKey": "xx",
            "panName": "小飞机网盘",
            "type": "fj",
            "sharePassword": "",
            "shareUrl": "https://share.feijipan.com/s/xx",
            "standardUrl": "https://www.feijix.com/s/xx",
            "otherParam": {
                "UA": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
            },
            "cacheKey": "fj:xx"
        }
    },
    "timestamp": 1736489219402
}
```
3. 文件夹解析(仅支持蓝奏云/蓝奏优享/小飞机网盘)
```json
{
  "code": 200,
  "msg": "success",
  "success": true,
  "data": [
    {
      "fileName": "xxx",
      "fileId": "xxx",
      "fileIcon": null,
      "size": 999,
      "sizeStr": "999 M",
      "fileType": "apk",
      "filePath": null,
      "createTime": "17 小时前",
      "updateTime": null,
      "createBy": null,
      "description": null,
      "downloadCount": null,
      "panType": "lz",
      "parserUrl": "下载链接", 
      "extParameters": null
    }
  ]
}
```
4. 解析次数统计接口 /v2/statisticsInfo
```json
{
    "code": 200,
    "msg": "success",
    "success": true,
    "count": 0,
    "data": {
        "parserTotal": 320508,
        "cacheTotal": 5957910,
        "total": 6278418
    },
    "timestamp": 1736489378770
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


| 网盘名称        | 免登陆下载分享 | 加密分享     | 初始网盘空间    | 单文件大小限制         |
|-------------|---------|----------|-----------|-----------------|
| 蓝奏云         | √       | √        | 不限空间      | 100M            | 
| 奶牛快传        | √       | X        | 10G       | 不限大小            | 
| 移动云云空间(个人版) | √       | √(密码可忽略) | 5G(个人)    | 不限大小            |
| 小飞机网盘       | √       | √(密码可忽略) | 10G       | 不限大小            | 
| 360亿方云      | √       | √(密码可忽略) | 100G(须实名) | 不限大小            | 
| 123云盘       | √       | √        | 2T        | 100G（>100M需要登录） | 
| 文叔叔         | √       | √        | 10G       | 5GB             | 
| 夸克网盘        | x       | √        | 10G       | 不限大小            | 
| UC网盘        | x       | √        | 10G       | 不限大小            | 

# 打包部署

## JDK下载（lz.qaiu.top提供直链云解析服务）
- [阿里jdk17(Dragonwell17-windows-x86)](https://lz.qaiu.top/ec/e957acef36ce89e1053979672a90d219n)
- [阿里jdk17(Dragonwell17-linux-x86)](https://lz.qaiu.top/ec/6ebc9f2e0bbd53b4c4d5b11013f40a80NHvcYU)
- [阿里jdk17(Dragonwell17-linux-aarch64)](https://lz.qaiu.top/ec/d14c2d06296f61b52a876b525265e0f8tzxTc5)
- [解析有效性测试-移动云云空间-阿里jdk17-linux-x86](https://lz.qaiu.top/json/ec/6ebc9f2e0bbd53b4c4d5b11013f40a80NHvcYU)

## 开发和打包

```shell
# 环境要求: Jdk17 + maven;
mvn clean
mvn package

```
打包好的文件位于 web-service/target/netdisk-fast-download-bin.zip
## Linux服务部署

### Docker 部署（Main分支）

#### 海外服务器Docker部署
```shell
# 创建目录
mkdir -p netdisk-fast-download
cd netdisk-fast-download

# 拉取镜像
docker pull ghcr.io/qaiu/netdisk-fast-download:main

# 复制配置文件（或下载仓库web-service\src\main\resources）
docker create --name netdisk-fast-download ghcr.io/qaiu/netdisk-fast-download:main
docker cp netdisk-fast-download:/app/resources ./resources
docker rm netdisk-fast-download

# 启动容器
docker run -d -it --name netdisk-fast-download -p 6401:6401 --restart unless-stopped -e TZ=Asia/Shanghai -v ./resources:/app/resources -v ./db:/app/db -v ./logs:/app/logs ghcr.io/qaiu/netdisk-fast-download:main

# 反代6401端口

# 升级容器
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock containrrr/watchtower --cleanup --run-once netdisk-fast-download
```

#### 国内Docker部署
```shell
# 创建目录
mkdir -p netdisk-fast-download
cd netdisk-fast-download

# 拉取镜像
docker pull ghcr.nju.edu.cn/qaiu/netdisk-fast-download:main

# 复制配置文件（或下载仓库web-service\src\main\resources）
docker create --name netdisk-fast-download ghcr.nju.edu.cn/qaiu/netdisk-fast-download:main
docker cp netdisk-fast-download:/app/resources ./resources
docker rm netdisk-fast-download

# 启动容器
docker run -d -it --name netdisk-fast-download -p 6401:6401 --restart unless-stopped -e TZ=Asia/Shanghai -v ./resources:/app/resources -v ./db:/app/db -v ./logs:/app/logs ghcr.nju.edu.cn/qaiu/netdisk-fast-download:main

# 反代6401端口

# 升级容器
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock containrrr/watchtower --cleanup --run-once netdisk-fast-download
```

### 宝塔部署指引 -> [点击进入宝塔部署教程](https://blog.qaiu.top/archives/netdisk-fast-download-bao-ta-an-zhuang-jiao-cheng)

### Linux命令行部署
> 注意: netdisk-fast-download.service中的ExecStart的路径改为实际路径
```shell
cd ~
wget -O netdisk-fast-download.zip  https://github.com/qaiu/netdisk-fast-download/releases/download/0.1.8-release-fixed2/netdisk-fast-download-bin-fixed2.zip
unzip netdisk-fast-download-bin.zip
cd netdisk-fast-download
bash service-install.sh
```
服务相关命令:  

查看服务状态  
`systemctl status netdisk-fast-download.service`
 
启动服务  
`systemctl start netdisk-fast-download.service`

重启服务  
`systemctl restart netdisk-fast-download.service`

停止服务  
`systemctl stop netdisk-fast-download.service`

开机启动服务  
`systemctl enable netdisk-fast-download.servic`

停止开机启动  
`systemctl disable netdisk-fast-download.servic`

## Windows服务部署
1. 下载并解压releases版本netdisk-fast-download-bin.zip
2. 进入netdisk-fast-download下的bin目录
3. 使用管理员权限运行nfd-service-install.bat
如果不想使用服务运行可以直接运行run.bat
> 注意: 如果jdk环境变量的java版本不是17请修改nfd-service-template.xml中的java命令的路径改为实际路径

## 相关配置说明

resources目录下包含服务端配置文件 配置文件自带说明，具体请查看配置文件内容，  
app-dev.yml 可以配置解析服务相关信息， 包括端口，域名，缓存时长等  
server-proxy.yml 可以配置代理服务运行的相关信息， 包括前端反向代理端口，路径等  

### ip代理配置说明  
有时候解析量很大，IP容易被ban，这时候可以使用其他服务器搭建nfd-proxy代理服务。

修改配置文件：
app-dev.yml

```yaml
proxy:
  - panTypes: pgd,pdb,pod     # 网盘标识
    type: http                # 支持http/socks4/socks5
    host: 127.0.0.1           # 代理IP
    port: 7890                # 端口
    username:                 # 用户名
    password:                 # 密码
```  
nfd-proxy搭建http代理服务器 
参考https://github.com/nfd-parser/nfd-proxy

## 0.1.9 开发计划
- 目录解析(专属版)
- 带cookie/token参数解析大文件(专属版)

**技术栈:**
Jdk17+Vert.x4
Core模块集成Vert.x实现类似spring的注解式路由API


## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=qaiu/netdisk-fast-download&type=Date)](https://star-history.com/#qaiu/netdisk-fast-download&Date)

## **免责声明**  
   - 用户在使用本项目时，应自行承担风险，并确保其行为符合当地法律法规及网盘服务提供商的使用条款。  
   - 开发者不对用户因使用本项目而导致的任何后果负责，包括但不限于数据丢失、隐私泄露、账号封禁或其他任何形式的损害。

## 支持该项目
开源不易，用爱发电，本项目长期维护如果觉得有帮助, 可以请作者喝杯咖啡, 感谢支持  

### 关于专属版
99元, 提供对小飞机,蓝奏优享大文件解析的支持, 提供天翼云盘,移动云盘,联调云盘的解析支持  
199元, 包含部署服务和首页定制, 需提供宝塔环境  
可以提供功能定制开发, 加v价格详谈:
<p>wechat1: qaiu-cn</p>
<p>wechat2: imcoding_</p>

<!--
![image](https://github.com/qaiu/netdisk-fast-download/assets/29825328/54276aee-cc3f-4ebd-8973-2e15f6295819)

[手机端支付宝打赏跳转链接](https://qr.alipay.com/fkx01882dnoxxtjenhlxt53)
-->




