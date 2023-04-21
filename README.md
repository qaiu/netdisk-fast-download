# lz-cow-api
蓝奏云-奶牛快传的直链解析的API服务  

示例:  
```
// 解析并重定向到直链
###
# @no-redirect
GET http://127.0.0.1:6400/parse?url=https://lanzoux.com/ia2cntg
###
# @no-redirect
GET http://127.0.0.1:6400/parse?url=https://cowtransfer.com/core/api/transfer/share?uniqueUrl=9a644fe3e3a748

// Rest请求(只提供共享文件Id): cow 奶牛快传; lz 蓝奏云
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


