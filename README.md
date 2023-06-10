# netdisk-fast-download
# 网盘快速下载器--直链解析
[![Java CI with Maven](https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml/badge.svg)](https://github.com/qaiu/netdisk-fast-download/actions/workflows/maven.yml)
## 网盘支持情况:  
` 网盘名称(网盘标识): ` 
- 蓝奏云 (lz)
    - [ ] 登录, 上传, 下载, 分享
    - [x] 直链解析
- 奶牛快传 (cow)
    - [ ] 登录, 上传, 下载, 分享
    - [x] 直链解析
- 移动云空间 (ec)
    - [ ] 登录, 上传, 下载, 分享
    - [x] 直链解析
- UC网盘 (uc)
  - [ ] 登录, 上传, 下载, 分享
  - [x] 直链解析
- 小飞机网盘 (fj)
  - [ ] 登录, 上传, 下载, 分享
  - [x] 直链解析
- 亿方云 (fc)
  - [ ] 登录, 上传, 下载, 分享
  - [x] 直链解析
- 文叔叔 (ws)
- 夸克网盘 (qk)
- TODO

技术栈: 
Jdk17+Vert.x4.4.1+Jsoup  
Core模块集成Vert.x实现类spring的注解式路由API  

API接口
```
括号内是可选内容: 表示当带有分享密码时需要加上密码参数 
parse接口加上参数pwd=密码;其他接口在分享Key后面加上@密码

1. 解析并自动302跳转 : 
    http(s)://you_host/parser?url=分享链接(&pwd=xxx)
    http(s)://you_host/网盘标识/分享id(@分享密码)
2. 获取解析后的直链--JSON格式
    http(s)://you_host/json/网盘标识/分享id(@分享密码)
3. 有些网盘的加密分享的密码可以忽略: 如移动云空间,小飞机网盘
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

TODO:  
解析蓝奏云加密链接


# 网盘对比

| 网盘名称       | 可直接下载分享    | 加密分享     | 初始网盘空间    | 单文件大小限制 | 登录接口 |
|------------|------------|----------|-----------|---------|------|
| 蓝奏云        | √          | √        | 不限空间      | 100M    | TODO |
| 奶牛快传       | √          | X        | 10G       | 不限大小    | TODO |
| 移动云空间      | √          | √(密码可忽略) | 5G(个人)    | 不限大小    | TODO |
| UC网盘       | √          | √        | 10G       | 不限大小    | TODO |
| 小飞机网盘      | √          | √(密码可忽略) | 10G       | 不限大小    | TODO |
| 360亿方云     | √(注意有流量限制) | √(密码可忽略) | 100G(须实名) | 不限大小    | TODO |
| 文叔叔(TODO)  | √(注意有时间限制) | √        | 10G       | 不限大小    | TODO |
| 夸克网盘(TODO) | 需要登录       | √        | 10G(20G)  | 不限大小    | TODO |

# 打包部署
TODO
