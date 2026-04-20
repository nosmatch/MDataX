import axios from 'axios'

const request = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 10000
})

request.interceptors.response.use(
    response => {
        const data = response.data
        if (data.code !== 200) {
            return Promise.reject(new Error(data.message || '请求失败'))
        }
        return data
    },
    error => {
        return Promise.reject(error)
    }
)

export default request
