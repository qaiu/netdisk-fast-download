# nfd-web 
使用vue3+element-plus打造
解析服务的前端页面, 提供API测试, 统计查询, 二维码生成等;  
20241101 支持剪切板链接自动识别解析, 一键生成短链  
20241111 vue框架升级为3.0      

![img_2.png](img/img_2.png)
![img.png](img/img.png)
![img_1.png](img/img_1.png)

## 关于如何将前端项目和java一块打包:  
1. 先打包前端模块
2. 运行`npm run build`
3. 项目部署后演示页面的代理端口是6401默认使用http, 如需https可以加nginx代理, 也可以使用本项目自带的代理服务和配置证书路径

## nginx配置
```nginx
    location / {
      proxy_pass http://127.0.0.1:6401;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
```

## Project setup
```
npm install
```

### Compiles and hot-reloads for development
```
npm run serve
```

### Compiles and minifies for production
```
npm run build
```

### Lints and fixes files
```
npm run lint
```

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).


## 参考项目
- https://github.com/HurryBy/CloudDiskAnalysis
- https://github.com/syhyz1990/panAI
