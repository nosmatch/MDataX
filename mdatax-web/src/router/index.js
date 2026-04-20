import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
    {
        path: '/',
        component: Layout,
        redirect: '/dashboard',
        children: [
            { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
            { path: 'integration', name: 'DataIntegration', component: () => import('../views/Integration.vue') },
            { path: 'development', name: 'DataDevelopment', component: () => import('../views/Development.vue') },
            { path: 'assets', name: 'DataAssets', component: () => import('../views/Assets.vue') },
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

export default router
