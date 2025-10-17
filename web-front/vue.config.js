
const path = require("path");

function resolve(dir) {
  return path.join(__dirname, dir)
}
const CompressionPlugin = require('compression-webpack-plugin');
const FileManagerPlugin = require('filemanager-webpack-plugin')

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
    plugins: [
      new CompressionPlugin({
        test: /\.js$|\.html$|\.css/, // 匹配文件
        threshold: 10240 // 对超过10k文件压缩
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
              { source: './nfd-front', destination: '../webroot/nfd-front' }
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
