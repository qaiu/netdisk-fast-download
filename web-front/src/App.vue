<template>
  <div id="app">
    <el-row :gutter="20">
      <el-card class="box-card">
        <div class="demo-basic--circle">
          <div class="block" style="text-align: center;">
            <img :height="150" src="../public/images/lanzou111.png" alt="lz"></img>
          </div>
        </div>
        <h3 style="text-align: center;">NFD网盘直链解析0.1.8_bate2(API演示)</h3>
        <div class="typo">
          <p style="text-align: center;">
            <span>
              <el-link href="https://github.com/qaiu/netdisk-fast-download" target="_blank" rel="nofollow">
              <u>GitHub</u></el-link>
            </span>
            <span style="margin-left: 30px">
              <el-link href="https://blog.qaiu.top/archives/netdisk-fast-download-bao-ta-an-zhuang-jiao-cheng" target="_blank"
                rel="nofollow"><u>宝塔部署安装教程</u>
              </el-link>
            </span>
            <span style="margin-left: 30px">
              <el-link href="https://blog.qaiu.top" target="_blank"
                     rel="nofollow"><u>QAIU博客</u>
              </el-link>
            </span></p>
          <p><strong>目前支持 </strong>蓝奏云/蓝奏云优享/小飞机盘/123云盘/奶牛快传/移动云云空间/亿方云/文叔叔/QQ邮箱文件中转站</p>
          <p>已加入缓存机制, 如果遇到解析出的下载链接失效的情况请及时到<a href="https://github.com/qaiu/netdisk-fast-download/issues">
            <u><strong>项目GitHub反馈</strong></u></a></p>
          <p>节点1: 回源请求数:{{ node1Info.parserTotal }}, 缓存请求数:{{ node1Info.cacheTotal }}, 总数:{{ node1Info.total }}</p>
<!--          <p>节点2: 成功:{{ node2Info.success }},失败:{{ node2Info.fail }},总数:{{ node2Info.total }}</p>-->
        </div>
        <hr>
        <div class="main" v-loading="isLoading">
          <div class="grid-content">
            <el-input placeholder="请粘贴分享链接" v-model="link" id="url" lass="input-with-select">
              <strong slot="prepend">分享链接</strong>
            </el-input>
            <el-input placeholder="请输入密码" v-model="password" id="url" lass="input-with-select">
                <strong slot="prepend">分享密码</strong>
            </el-input>
            <el-input v-show="respData.data" placeholder="解析地址" :value="getLink2" id="url" lass="input-with-select">
              <el-button slot="append" v-clipboard:copy="getLink2"
                         v-clipboard:success="onCopy"
                         v-clipboard:error="onError">点我复制
              </el-button>
            </el-input>
            <p style="text-align: center">
              <el-button style="" @click="onSubmit">解析测试</el-button>
              <el-button style="margin-left: 20px" @click="genMd">生成Markdown链接</el-button>
              <el-button style="margin-left: 20px" @click="generateQRCode">生成二维码</el-button>
              <el-button style="margin-left: 20px" @click="getTj">链接信息统计</el-button>
            </p>
          </div>
          <div v-if="respData.code" style="margin-top: 10px">
            <strong>解析结果: </strong>
            <json-viewer
              :value="respData"
              :expand-depth=5
              copyable
              boxed
              sort
            />
            <a :href="downUrl" v-show="downUrl">点击下载</a>
          </div>
          <el-row v-if="mdText" style="text-align: center">
            <el-col span="22">
              <el-input
                type="textarea"
                :autosize="{ minRows: 4, maxRows: 8}"
                placeholder="请输入内容"
                v-model="mdText">
              </el-input>
            </el-col>
            <el-col span="2">
              <el-button v-clipboard:copy="mdText"
                         v-clipboard:success="onCopy"
                         v-clipboard:error="onError">复制
              </el-button>
            </el-col>
          </el-row>
          <div style="text-align: center" v-show="showQrc">
            <div style="text-align: center"><el-link  target="_blank" :href="codeUrl">{{ codeUrl }}</el-link></div>
            <canvas  ref="qrcodeCanvas"></canvas>
            <div style="text-align: center"> 扫码下载 </div>
          </div>
          <div v-if="tjData.shareLinkInfo">
            <el-descriptions class="margin-top" title="分享详情" :column="1" border>
              <template slot="extra">
                <el-button type="primary" size="small">操作</el-button>
              </template>
              <el-descriptions-item label="网盘名称">{{ tjData.shareLinkInfo.panName }}</el-descriptions-item>
              <el-descriptions-item label="网盘标识">{{ tjData.shareLinkInfo.type }}</el-descriptions-item>
              <el-descriptions-item label="分享Key">{{ tjData.shareLinkInfo.shareKey }}</el-descriptions-item>
              <el-descriptions-item label="分享链接"> <el-link  target="_blank" :href="tjData.shareLinkInfo.shareUrl">{{ tjData.shareLinkInfo.shareUrl }}</el-link></el-descriptions-item>
              <el-descriptions-item label="jsonApi链接"> <el-link  target="_blank" :href="tjData.apiLink">{{ tjData.apiLink }}</el-link></el-descriptions-item>
              <el-descriptions-item label="302下载链接"> <el-link  target="_blank" :href="tjData.downLink">{{ tjData.downLink }}</el-link></el-descriptions-item>
              <el-descriptions-item label="解析次数">{{ tjData.parserTotal }}</el-descriptions-item>
              <el-descriptions-item label="缓存命中次数">{{ tjData.cacheHitTotal }}</el-descriptions-item>
              <el-descriptions-item label="总请求次数">{{ tjData.sumTotal }}</el-descriptions-item>
            </el-descriptions>
          </div>

        </div>
      </el-card>
    </el-row>
  </div>
</template>

<script>
import axios from 'axios'
import QRCode from 'qrcode'
/*
蓝奏云 (lz)
 登录, 上传, 下载, 分享
 直链解析
奶牛快传 (cow)
 登录, 上传, 下载, 分享
 直链解析
移动云空间 (ec)
 登录, 上传, 下载, 分享
 直链解析
UC网盘 (uc)似乎已经失效，需要登录
 登录, 上传, 下载, 分享
 直链解析
小飞机网盘 (fj)
 登录, 上传, 下载, 分享
 直链解析
亿方云 (fc)
 登录, 上传, 下载, 分享
 直链解析
123云盘 (ye)
 登录, 上传, 下载,, 分享
 */
export default {
  name: 'App',
  data() {
    return {
      // baseAPI: `${location.protocol}//${location.hostname}:6400`,
      baseAPI: `${location.protocol}//${location.host}`,
      current: {}, // 当前分享
      showQrc: false,
      codeUrl: '',
      mdText: '',
      link: "",
      password: "",
      isLoading: false,
      downUrl: null,
      select: "lz",
      respData: {},
      tjData: {},
      panList: [
        {
          name: "蓝奏云",
          value: 'lz'
        },
        {
          name: "奶牛快传",
          value: 'cow'
        },
        {
          name: "移动云空间",
          value: 'ec'
        },
        {
          name: "UC网盘",
          value: 'uc',
          disabled: true
        },
        {
          name: "小飞机网盘",
          value: 'fj'
        },
        {
          name: "360亿方云",
          value: 'fc'
        },
        {
          name: "123云盘",
          value: 'ye'
        },
      ],
      getLink: null,
      getLink2: '',
      getLinkInfo:  null,
      node1Info: {},
      node2Info: {},
    }
  },
  methods: {
    check() {
      this.mdText = ''
      this.showQrc = false
      this.respData = {}
      this.tjData = {}
      if (!this.link.startsWith("https://")) {
        this.$message.error("请输入有效链接!")
        throw new Error('请输入有效链接')
      }
    },
    onSubmit() {
      this.check()
      this.isLoading = true
      this.downUrl = ''
      this.respData = {}
      this.getLink2 = `${this.baseAPI}/parser?url=${this.link}`
      // this.getLink = `${location.protocol}//${location.host}/api/json/parser?url=${this.link}`
      //   this.getLink = `${location.protocol}//${location.host}/json/parser`
      if (this.password) {
        this.getLink2 += `&pwd=${this.password}`
      }
      axios.get(this.getLink, {params: {url: this.link, pwd: this.password}}).then(
        response => {
          this.isLoading = false
          this.respData = response.data
          if (response.data.code === 200) {
            this.$message({
              message: response.data.msg,
              type: 'success'
            })
            this.downUrl = response.data.data.directLink
          } else {
            this.$message.error(response.data.msg)
          }
          this.getInfo()
        },
        error => {
          this.isLoading = false
          this.$message.error(error.message)
        }
      )
    },
    onCopy() {
      this.$message.success('复制成功')
    },
    onError() {
      this.$message.error('复制失败')
    },
    getInfo() {
      // 初始化统计信息
      axios.get('/n1/statisticsInfo').then(
        response => {
          if (response.data.success) {
            this.node1Info = response.data.data
          }
        })
      // axios.get('/n2/statisticsInfo').then(
      //   response => {
      //     if (response.data.success) {
      //       this.node2Info = response.data.data
      //     }
      //   })
    },
    genMd() {
      this.check()
      axios.get(this.getLinkInfo, {params: {url: this.link, pwd: this.password}}).then(
        response => {
          this.isLoading = false
          if (response.data.code === 200) {
            this.$message({
              message: response.data.msg,
              type: 'success'
            })
            this.mdText = this.buildMd('快速下载地址',response.data.data.downLink)
          } else {
            this.$message.error(response.data.msg)
          }
        });
    },
    buildMd(title, url) {
      return `[${title}](${url})`
    },
    generateQRCode() {
      this.check()
      const options = { // 设置二维码的参数，例如大小、边距等
        width: 150,
        height: 150,
        margin: 2,
        colorDark : "#8e0fb7",
        colorLight : "#fff2ad",
      };
      axios.get(this.getLinkInfo, {params: {url: this.link, pwd: this.password}}).then(
        response => {
          this.isLoading = false
          if (response.data.code === 200) {
            this.$message({
              message: response.data.msg,
              type: 'success'
            })
            this.codeUrl = response.data.data.downLink
            QRCode.toCanvas(this.$refs.qrcodeCanvas, this.codeUrl, options, error => {
              if (error) console.error(error);
            });
            this.showQrc = true
          } else {
            this.$message.error(response.data.msg)
          }
        });
    },
    getTj() {
      this.check()
      axios.get(this.getLinkInfo, {params: {url: this.link, pwd: this.password}}).then(
        response => {
          this.isLoading = false
          if (response.data.code === 200) {
            this.$message({
              message: response.data.msg,
              type: 'success'
            })
            this.tjData = response.data.data
          } else {
            this.$message.error(response.data.msg)
          }
        });
    }
  },
  mounted() {
    this.getLinkInfo = `${this.baseAPI}/v2/linkInfo`
    this.getLink = `${this.baseAPI}/json/parser`
    this.getInfo()
  }
}
</script>

<style>
#app {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  margin: auto;
  padding: 1em;
  max-width: 900px;
}

::selection {
  background: rgba(0, 149, 255, .1);
}

body:before {
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: .3;
  z-index: -1;
  content: "";
  position: fixed;
}

.grid-content {
  margin-top: 1em;
  border-radius: 4px;
  min-height: 50px;
}

.el-select .el-input {
  width: 130px;
}

.input-with-select .el-input-group__prepend {
  background-color: #fff;

}

.box-card {
  margin-top: 4em !important;
  margin-bottom: 4em !important;
  opacity: .8;
}

@media screen and (max-width: 700px) {
  .box-card {
    margin-top: 1em !important;
    margin-bottom: 1em !important;
  }
}

.download h3 {
  margin-top: 2em;
}

.download button {
  margin-right: 0.5em;
  margin-left: 0.5em;
}

.typo {
  text-align: left;
}

.typo a {
  color: #0077ff;
}

hr {
  height: 10px;
  margin-bottom: .8em;
  border: none;
  border-bottom: 1px solid rgba(0, 0, 0, .12);
}
</style>
