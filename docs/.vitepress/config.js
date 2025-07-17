import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Netdisk Fast Download',
  description: '网盘分享链接云解析服务',
  lang: 'zh-CN',
  
  // Clean URLs without .html extension
  cleanUrls: true,
  
  // Base path for GitHub Pages
  base: '/netdisk-fast-download/',
  
  head: [
    ['link', { rel: 'icon', href: '/netdisk-fast-download/favicon.ico' }],
    ['meta', { name: 'theme-color', content: '#3c8772' }]
  ],

  themeConfig: {
    logo: '/logo.svg',
    
    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/guide/getting-started' },
      { text: 'API 文档', link: '/api/' },
      { text: '部署指南', link: '/deployment/' },
      { text: 'GitHub', link: 'https://github.com/qaiu/netdisk-fast-download' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: '指南',
          items: [
            { text: '快速开始', link: '/guide/getting-started' },
            { text: '网盘支持', link: '/guide/supported-platforms' },
            { text: '配置说明', link: '/guide/configuration' }
          ]
        }
      ],
      '/api/': [
        {
          text: 'API 文档',
          items: [
            { text: 'API 概览', link: '/api/' },
            { text: '解析接口', link: '/api/parse' },
            { text: '文件夹接口', link: '/api/folder' },
            { text: '统计接口', link: '/api/statistics' },
            { text: '示例代码', link: '/api/examples' }
          ]
        }
      ],
      '/deployment/': [
        {
          text: '部署指南',
          items: [
            { text: '部署概览', link: '/deployment/' },
            { text: 'Docker 部署', link: '/deployment/docker' },
            { text: 'Linux 部署', link: '/deployment/linux' },
            { text: 'Windows 部署', link: '/deployment/windows' },
            { text: '宝塔面板部署', link: '/deployment/baota' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/qaiu/netdisk-fast-download' }
    ],

    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2024 netdisk-fast-download'
    },

    editLink: {
      pattern: 'https://github.com/qaiu/netdisk-fast-download/edit/main/docs/:path',
      text: '在 GitHub 上编辑此页'
    },

    lastUpdated: {
      text: '最后更新于',
      formatOptions: {
        dateStyle: 'short',
        timeStyle: 'medium'
      }
    },

    docFooter: {
      prev: '上一页',
      next: '下一页'
    },

    outline: {
      label: '页面导航'
    },

    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: '搜索文档',
                buttonAriaLabel: '搜索文档'
              },
              modal: {
                noResultsText: '无法找到相关结果',
                resetButtonTitle: '清除查询条件',
                footer: {
                  selectText: '选择',
                  navigateText: '切换'
                }
              }
            }
          }
        }
      }
    }
  },

  markdown: {
    lineNumbers: true
  }
})