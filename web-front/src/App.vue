<template>
  <div id="app">
    <el-row :gutter="20">
      <el-card class="box-card">
        <div class="demo-basic--circle">
          <div class="block" style="text-align: center;">
            <el-avatar :size="150" :src="avatar"></el-avatar>
          </div>
        </div>
        <h1 style="text-align: center;">netdisk-fast-download网盘直链解析</h1>
        <div class="typo">
          <p><strong>项目地址 </strong><a href="https://github.com/qaiu/netdisk-fast-download" target="_blank"
                                      rel="nofollow"><u>点我跳转</u></a></p>
          <p><strong>目前支持 </strong>已支持蓝奏云/奶牛快传/移动云云空间/UC网盘(暂时失效)/小飞机盘/亿方云/123云盘</p>
        </div>
        <hr>
        <div class="main" v-loading="isLoading">
          <div class="grid-content">
            <el-input placeholder="请粘贴分享链接" v-model="link" id="url" lass="input-with-select">
              <el-select v-model="select" slot="prepend" placeholder="">
                <el-option v-for="item in panList" :key="item.value" :value="item.value" :label="item.name"
                           :disabled="item.disabled"/>
              </el-select>
              <el-button slot="append" @click="onSubmit">解析</el-button>
            </el-input>
            <el-input placeholder="请输入密码" v-model="password" id="url" lass="input-with-select"></el-input>
            <el-input placeholder="解析地址" v-value="link" id="url" lass="input-with-select">
              <el-button slot="append"  v-clipboard:copy="getLink"
                         v-clipboard:success="onCopy"
                         v-clipboard:error="onError">点我复制</el-button>
            </el-input>
          </div>
          <div v-show="respData" style="margin-top: 10px">
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
        </div>
      </el-card>
    </el-row>
  </div>
</template>

<script>
import axios from 'axios'
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
 登录, 上传, 下载,
 */
export default {
  name: 'App',
  data: function () {
    return {
      link: "",
      password: "",
      isLoading: false,
      downUrl: null,
      avatar: "https://q2.qlogo.cn/headimg_dl?dst_uin=736226400&spec=640",
      select: "lz",
      respData: null,
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
      getLink: ''
    }
  },
  methods: {
    onSubmit() {
      if (!this.link.startsWith("https://")) {
        this.$message.error("请输入有效链接!")
        return
      }
      this.isLoading = true
      this.downUrl = ''
      this.respData = ''
      this.getLink = `http://127.0.0.1:6400/json/parser?url=${this.link}`
      if (this.password) {
        this.getLink += `&pwd=${this.password}`
      }
      axios.get(this.getLink).then(
        response => {
          if (response.data.code === 200) {
            this.$message({
              message: response.data.msg,
              type: 'success'
            })
            this.isLoading = false
            this.downUrl = response.data.data
            this.respData = response.data
          } else {
            this.isLoading = false
            this.respData = response.data
            this.$message.error(response.data.msg)
          }
        },
        error => {
          this.isLoading = false
          this.$message.error(error.message)
        }
      )
    },
    downloadFile(downloadUrl) {
      window.location.href = downloadUrl
    },
    onCopy(){
      this.$message.success('复制成功')
    },
    onError(){
      this.$message.error('复制失败')
    }
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


.item {
  padding: 5px;
  break-inside: avoid;
}

.item img {
  width: 100%;
  margin-bottom: 10px;
}

.typo {
  text-align: left;
}

.typo a {
  color: #2c3e50;
  text-decoration: none;
}

hr {
  height: 10px;
  margin-bottom: .8em;
  border: none;
  border-bottom: 1px solid rgba(0, 0, 0, .12);
}
</style>
