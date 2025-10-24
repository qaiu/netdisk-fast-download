import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import ShowFile from '@/views/ShowFile.vue'
import ShowList from '@/views/ShowList.vue'
import ClientLinks from '@/views/ClientLinks.vue'

const routes = [
  { path: '/', component: Home },
  { path: '/showFile', component: ShowFile },
  { path: '/showList', component: ShowList },
  { path: '/clientLinks', component: ClientLinks }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

export default router 