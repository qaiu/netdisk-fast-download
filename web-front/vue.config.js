const { defineConfig } = require('@vue/cli-service')
const path = require("path");
module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false
})

function resolve(dir) {
  return path.join(__dirname, dir)
}
const CompressionPlugin = require('compression-webpack-plugin');
const FileManagerPlugin = require('filemanager-webpack-plugin')

module.exports = {
  outputDir: 'nfd-front',
  devServer: {
    host: '127.0.0.1',
    port: 6444,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:6400',  // 请求本地
        changeOrigin: true,
        pathRewrite: {
          '^/api': '/'   //本身的接口地址没有 '/api' 这种通用前缀，所以要rewrite，如果本身有则去掉
        },
        ws: true
      },
    }
  },
  configureWebpack: {
    // provide the app's title in webpack's name field, so that
    // it can be accessed in index.html to inject the correct title.
    name: 'Netdisk fast download',
    resolve: {
      alias: {
        '@': resolve('src')
      }
    },

    plugins: [
      new CompressionPlugin({
        test: /\.js$|\.html$|\.css/, // 匹配文件
        threshold: 10240 // 对超过10k文件压缩
      }),
      new FileManagerPlugin({  //初始化 filemanager-webpack-plugin 插件实例
        onEnd: {
          mkdir: ['./nfd-front'],
          delete: [   //首先需要删除项目根目录下的dist.zip
            './nfd-front.zip',
          ],
          archive: [ //然后我们选择dist文件夹将之打包成dist.zip并放在根目录
            {source: './nfd-front', destination: './nfd-front.zip'},
          ]
        }
      })
    ]
  },

}
