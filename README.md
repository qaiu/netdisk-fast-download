# netdisk-fast-download
# 网盘快速下载器--直链解析

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
- 夸克网盘 (qk)
- TODO

技术栈: 
Jdk17+Vert.x4.4.1+Jsoup  
Core模块集成Vert.x实现类spring的注解式路由API  

API接口
```shell
(括号内表示可选内容)
1. 解析并自动302跳转 : 
    http(s)://you_host/parser?url=分享链接
    http(s)://you_host/网盘标识/分享id(#分享密码)
2. 获取解析后的直链--JSON格式
    http(s)://you_host/网盘标识/分享id(#分享密码)

```


示例:  
```
// 解析并重定向到直链
###
# @no-redirect
GET http://127.0.0.1:6400/parser?url=https://lanzoux.com/ia2cntg
###
# @no-redirect
GET http://127.0.0.1:6400/parser?url=https://cowtransfer.com/s/9a644fe3e3a748

// Rest请求(只提供共享文件Id):
###
# @no-redirect
GET http://127.0.0.1:6400/cow/9a644fe3e3a748

// 解析返回json直链
###
GET http://127.0.0.1:6400/json/cow/9a644fe3e3a748
###
GET http://127.0.0.1:6400/json/lz/ia2cntg

```

TODO:  
解析蓝奏云加密链接


# 网盘对比

| 网盘名称  | 可直接下载分享     | 加密分享     | 初始网盘空间   | 单文件大小限制        | 登录接口 |
|-------|-------------|----------|----------|----------------|------|
| 蓝奏云   | √           | √        | 不限空间     | 100M           | TODO |
| 奶牛快传  | √           | X        | 10G      | 不限大小           | TODO |
| 移动云空间 | √           | √(密码可忽略) | 5G(个人)   | 不限大小           | TODO |
| UC网盘  | √           | √        | 10G      | 不限大小           | TODO |
| 夸克网盘  | √(>10M需要登录) | √        | 10G(20G) | 不限大小(>10M需要登录) | X    |

