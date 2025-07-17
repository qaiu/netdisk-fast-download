# 文档说明

本项目使用 [VitePress](https://vitepress.dev/) 构建文档网站。

## 本地开发

### 安装依赖
```bash
cd docs
npm install
```

### 启动开发服务器
```bash
npm run docs:dev
```

访问 http://localhost:5173 查看文档。

### 构建生产版本
```bash
npm run docs:build
```

构建完成后，静态文件位于 `.vitepress/dist` 目录。

### 预览生产版本
```bash
npm run docs:preview
```

## 文档结构

```
docs/
├── .vitepress/
│   ├── config.js          # VitePress 配置
│   └── dist/              # 构建输出目录
├── api/                   # API 文档
│   ├── index.md          # API 概览
│   ├── parse.md          # 解析接口
│   ├── folder.md         # 文件夹接口
│   ├── statistics.md     # 统计接口
│   └── examples.md       # 示例代码
├── guide/                 # 使用指南
│   ├── getting-started.md # 快速开始
│   ├── supported-platforms.md # 支持平台
│   └── configuration.md  # 配置说明
├── deployment/            # 部署指南
│   ├── index.md          # 部署概览
│   ├── docker.md         # Docker 部署
│   ├── linux.md          # Linux 部署
│   ├── windows.md        # Windows 部署
│   └── baota.md          # 宝塔部署
├── index.md              # 首页
├── package.json          # 依赖配置
└── README.md            # 本文件
```

## 部署到 GitHub Pages

项目配置了 GitHub Actions 自动部署，当 `main` 分支的 `docs/` 目录有变更时会自动触发构建和部署。

手动部署步骤：
1. 推送代码到 `main` 分支
2. GitHub Actions 自动构建文档
3. 部署到 GitHub Pages

## 编写文档

### Markdown 扩展

VitePress 支持 Markdown 扩展语法：

#### 代码块
```javascript
console.log('Hello World');
```

#### 信息框
::: tip 提示
这是一个提示信息框
:::

::: warning 警告
这是一个警告信息框
:::

::: danger 危险
这是一个危险信息框
:::

#### 自定义容器
::: details 点击展开
这里是详细内容
:::

### 配置修改

主要配置文件：`.vitepress/config.js`

- 修改网站标题、描述
- 配置导航菜单
- 设置侧边栏
- 自定义主题样式

### 添加新页面

1. 在相应目录创建 `.md` 文件
2. 在 `config.js` 中更新侧边栏配置
3. 使用相对路径进行内部链接

## 本地化

文档已配置为中文，支持：
- 中文界面
- 中文搜索
- 中文日期格式
- 中文导航文本

## 自定义

### 主题定制

可以通过以下方式自定义主题：
- 修改 CSS 变量
- 自定义组件
- 添加自定义样式

### 插件扩展

VitePress 支持 Vite 插件生态：
- 图片优化
- PWA 支持
- 其他扩展功能

## 故障排除

### 常见问题

1. **构建失败**：检查 Markdown 语法和链接
2. **样式异常**：清理缓存重新构建
3. **链接失效**：使用相对路径，注意大小写

### 调试模式

```bash
# 详细日志
DEBUG=vitepress:* npm run docs:dev

# 检查死链
npm run docs:build --debug
```