import { createRouter, createWebHistory } from 'vue-router'
import { TOKEN_STORAGE_KEY } from '@/api/http'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: () => import('@/views/auth/RegisterView.vue'), meta: { guestOnly: true } },
    { path: '/register/profile', name: 'register-profile', component: () => import('@/views/auth/ProfileSetupView.vue'), meta: { guestOnly: true } },
    { path: '/home/index', name: 'home', component: () => import('@/views/home/HomeView.vue'), meta: { requiresAuth: true } },
    { path: '/detail/index/:id', name: 'detail', component: () => import('@/views/content/DetailView.vue'), meta: { requiresAuth: true } },
    { path: '/release', name: 'release', component: () => import('@/views/content/EditorView.vue'), meta: { requiresAuth: true } },
    { path: '/release/pic', redirect: (to) => ({ path: '/release', query: to.query }) },
    { path: '/release/letter', redirect: '/release' },
    { path: '/my/index', name: 'profile', component: () => import('@/views/profile/ProfileView.vue'), meta: { requiresAuth: true } },
    { path: '/search', name: 'search', component: () => import('@/views/search/SearchView.vue'), meta: { requiresAuth: true } },
    { path: '/agent', name: 'agent', component: () => import('@/views/agent/AgentView.vue'), meta: { requiresAuth: true } },
    { path: '/agent/traces', name: 'agent-traces', component: () => import('@/views/trace/TraceView.vue'), meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})

router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem(TOKEN_STORAGE_KEY))
  if (to.meta.requiresAuth && !hasToken) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.guestOnly && hasToken) return { name: 'home' }
})

export default router
