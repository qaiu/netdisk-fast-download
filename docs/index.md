---
layout: home

hero:
  name: "Netdisk Fast Download"
  text: "网盘分享链接云解析服务"
  tagline: "快速解析各大网盘分享链接，获取直链下载地址"
  image:
    src: https://github.com/user-attachments/assets/87401aae-b0b6-4ffb-bbeb-44756404d26f
    alt: Netdisk Fast Download
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/getting-started
    - theme: alt
      text: API 文档
      link: /api/
    - theme: alt
      text: 在 GitHub 查看
      link: https://github.com/qaiu/netdisk-fast-download

features:
  - icon: 🚀
    title: 多网盘支持
    details: 支持蓝奏云、奶牛快传、移动云、小飞机盘、亿方云、123云盘等多款主流网盘
  - icon: 🔒
    title: 加密分享支持
    details: 支持带密码的加密分享链接解析，自动处理验证流程
  - icon: ⚡
    title: 高性能解析
    details: 基于 Vert.x 4 构建，提供高性能的并发解析能力
  - icon: 🔧
    title: 易于部署
    details: 提供 Docker、Linux、Windows 多种部署方式，支持一键部署
  - icon: 📁
    title: 文件夹支持
    details: 支持部分网盘的文件夹分享链接解析
  - icon: 🎯
    title: RESTful API
    details: 提供完整的 RESTful API 接口，支持多种调用方式
---

## 快速体验

你可以通过以下预览地址快速体验服务：

- [预览地址1](https://lz.qaiu.top)
- [预览地址2](http://www.722shop.top:6401)

## 技术栈

- **后端**: JDK 17 + Vert.x 4
- **前端**: Vue 3 + Element Plus
- **构建**: Maven + Webpack

## 网盘支持情况

| 网盘名称 | 免登陆下载 | 加密分享 | 文件夹支持 | 网盘标识 |
|---------|----------|----------|------------|----------|
| 蓝奏云 | ✅ | ✅ | ✅ | lz |
| 奶牛快传 | ✅ | ❌ | ❌ | cow |
| 移动云云空间 | ✅ | ✅ | ❌ | ec |
| 小飞机网盘 | ✅ | ✅ | ✅ | fj |
| 亿方云 | ✅ | ✅ | ❌ | fc |
| 123云盘 | ✅ | ✅ | ❌ | ye |

## 获取支持

- **QQ群**: 1017480890
- **GitHub Issues**: [提交问题](https://github.com/qaiu/netdisk-fast-download/issues)

::: warning 注意事项
- 请不要过度依赖预览地址服务，建议本地搭建或云服务器自行搭建
- 解析次数过多IP会被部分网盘厂商限制，不推荐做公共解析
- 小飞机解析有IP限制，多数云服务商的大陆IP会被拦截
:::