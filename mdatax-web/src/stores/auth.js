import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
    const token = ref(localStorage.getItem('token') || '')
    const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))

    const setToken = (t) => {
        token.value = t
        localStorage.setItem('token', t)
    }

    const setUser = (u) => {
        user.value = u
        localStorage.setItem('user', JSON.stringify(u))
    }

    const clearAuth = () => {
        token.value = ''
        user.value = null
        localStorage.removeItem('token')
        localStorage.removeItem('user')
    }

    const isLoggedIn = () => !!token.value

    return { token, user, setToken, setUser, clearAuth, isLoggedIn }
})
