import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/',
        component: () => import('../views/Layout.vue'),
        redirect: '/dashboard',
        children: [
            { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
            { path: 'integration', name: 'DataIntegration', component: () => import('../views/Integration.vue') },
            { path: 'development', name: 'DataDevelopment', component: () => import('../views/Development.vue') },
            { path: 'development/tasks', name: 'TaskManagement', component: () => import('../views/TaskManagement.vue') },
            { path: 'workflows', name: 'WorkflowManagement', component: () => import('../views/WorkflowManagement.vue') },
            { path: 'assets', name: 'DataAssets', component: () => import('../views/Assets.vue') },
            { path: 'assets/detail/:id', name: 'AssetsDetail', component: () => import('../views/AssetsDetail.vue') },
            { path: 'query', name: 'DataQuery', component: () => import('../views/Query.vue') },
            { path: 'permission', name: 'Permission', component: () => import('../views/Permission.vue') },
            { path: 'system', name: 'System', component: () => import('../views/System.vue') },
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const authStore = useAuthStore()
    if (to.path !== '/login' && !authStore.isLoggedIn()) {
        next('/login')
    } else if (to.path === '/login' && authStore.isLoggedIn()) {
        next('/')
    } else {
        next()
    }
})

export default router
