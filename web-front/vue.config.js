
const path = require("path");

function resolve(dir) {
  return path.join(__dirname, dir)
}

const CompressionPlugin = require('compression-webpack-plugin');
const FileManagerPlugin = require('filemanager-webpack-plugin');
const MonacoEditorPlugin = require('monaco-editor-webpack-plugin');

module.exports = {
  productionSourceMap: false, // 是否在构建生产包时生成sourceMap文件，false将提高构建速度
  transpileDependencies: true,
  lintOnSave: false,
  outputDir: 'nfd-front',
  devServer: {
    host: '127.0.0.1',
    port: 6444,
    proxy: {
      '/parser': {
        target: 'http://127.0.0.1:6400/',  // 请求本地
        ws: false
      },
      '/v2': {
        target: 'http://127.0.0.1:6400/',  // 请求本地
        ws: false
      },
      '/json': {
        target: 'http://127.0.0.1:6400/',  // 请求本地
        ws: false
      },
      '/d': {
        target: 'http://127.0.0.1:6400/',  // 请求本地
        ws: false
      },
    }
  },
  configureWebpack: {
    // provide the app's title in webpack's name field, so that
    // it can be accessed in list.html to inject the correct title.
    name: 'Netdisk fast download',
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },
    // Monaco Editor配置 - 使用本地打包
    module: {
      rules: [
        {
          test: /\.ttf$/,
          type: 'asset/resource'
        }
      ]
    },
    plugins: [
      new MonacoEditorPlugin({
        languages: ['javascript', 'typescript', 'json'],
        features: ['coreCommands', 'find', 'format', 'suggest', 'quickCommand'],
        publicPath: process.env.NODE_ENV === 'production' ? './' : '/',
        // Worker 文件输出路径
        filename: 'js/[name].worker.js'
      }),
      new CompressionPlugin({
        test: /\.js$|\.html$|\.css/, // 匹配文件
        threshold: 10240, // 对超过10k文件压缩
        // 排除 js 目录下的 worker 文件（Monaco Editor 使用 vs/assets 下的）
        exclude: /js\/.*\.worker\.js$/
      }),
      new FileManagerPlugin({  //初始化 filemanager-webpack-plugin 插件实例
        events: {
          onEnd: {
            mkdir: ['./nfd-front'],
            delete: [
              { source: './nfd-front.zip', options: { force: true } },
              { source: '../webroot/nfd-front', options: { force: true } },
              { source: './nfd-front/view/.git', options: { force: true } },
              { source: './nfd-front/view/.gitignore', options: { force: true } },
              { source: '../webroot/nfd-front/view/.git', options: { force: true } },
              { source: '../webroot/nfd-front/view/.gitignore', options: { force: true } },
            ],
            copy: [
              // 复制 Monaco Editor 的 vs 目录到 js/vs
              { 
                source: './node_modules/monaco-editor/min/vs', 
                destination: './nfd-front/js/vs' 
              }
            ],
            archive: [ //然后我们选择dist文件夹将之打包成dist.zip并放在根目录
              {
                source: './nfd-front', destination: './nfd-front.zip', options: {}
              },
            ]
          }
        }
      })
    ]
  },

}
