import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import ShowFile from '@/views/ShowFile.vue'
import ShowList from '@/views/ShowList.vue'
import ClientLinks from '@/views/ClientLinks.vue'
import Playground from '@/views/Playground.vue'

const routes = [
  { path: '/', component: Home },
  { path: '/showFile', component: ShowFile },
  { path: '/showList', component: ShowList },
  { path: '/clientLinks', component: ClientLinks },
  { path: '/playground', component: Playground },
  // 404页面 - 必须放在最后
  { 
    path: '/:pathMatch(.*)*', 
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

export default router 