import axios from 'axios'
import { useAuthStore } from '../stores/auth.js'

const request = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 10000
})

request.interceptors.request.use(
    config => {
        const authStore = useAuthStore()
        if (authStore.token) {
            config.headers.Authorization = 'Bearer ' + authStore.token
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

request.interceptors.response.use(
    response => {
        const data = response.data
        if (data.code !== 200) {
            if (data.code === 401) {
                const authStore = useAuthStore()
                authStore.clearAuth()
                window.location.href = '/login'
            }
            return Promise.reject(new Error(data.message || '请求失败'))
        }
        return data
    },
    error => {
        if (error.response && error.response.status === 401) {
            const authStore = useAuthStore()
            authStore.clearAuth()
            window.location.href = '/login'
        }
        return Promise.reject(error)
    }
)

export default request
