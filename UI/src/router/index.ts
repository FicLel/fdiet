import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import DietBuilderView from '@/views/DietBuilderView.vue'

/**
 * The patient's own views — the week grid and the day view — come later; the
 * builder is the screen the nutritionist works in, and the one the app opens on
 * while it is the only one there is.
 */
const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dieta' },
  { path: '/dieta', name: 'diet-builder', component: DietBuilderView },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
