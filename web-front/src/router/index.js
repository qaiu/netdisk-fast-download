import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import ShowFile from '@/views/ShowFile.vue'
import ShowList from '@/views/ShowList.vue'

const routes = [
  { path: '/', component: Home },
  { path: '/showFile', component: ShowFile },
  { path: '/showList', component: ShowList }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

export default router 