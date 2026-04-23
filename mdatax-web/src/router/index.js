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
            { path: 'datasource', name: 'Datasource', component: () => import('../views/Integration.vue') },
            { path: 'sync-task', name: 'SyncTask', component: () => import('../views/Integration.vue') },
            { path: 'development', name: 'DataDevelopment', component: () => import('../views/Development.vue') },
            { path: 'development/tasks', name: 'TaskManagement', component: () => import('../views/TaskManagement.vue') },
            { path: 'workflows', name: 'WorkflowManagement', component: () => import('../views/WorkflowManagement.vue') },
            { path: 'assets', name: 'DataAssets', component: () => import('../views/Assets.vue') },
            { path: 'assets/detail/:id', name: 'AssetsDetail', component: () => import('../views/AssetsDetail.vue') },
            { path: 'query', name: 'DataQuery', component: () => import('../views/Query.vue') },
            { path: 'permission', name: 'Permission', component: () => import('../views/Permission.vue') },
            { path: 'my-applies', name: 'MyApplies', component: () => import('../views/MyApplies.vue') },
            { path: 'my-approvals', name: 'MyApprovals', component: () => import('../views/MyApprovals.vue') },
            { path: 'report', name: 'ReportList', component: () => import('../views/ReportList.vue') },
            { path: 'report/edit', name: 'ReportCreate', component: () => import('../views/ReportEdit.vue') },
            { path: 'report/edit/:id', name: 'ReportEdit', component: () => import('../views/ReportEdit.vue') },
            { path: 'report/view/:id', name: 'ReportView', component: () => import('../views/ReportView.vue') },
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
