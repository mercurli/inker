import axios from 'axios'

const http = axios.create({
  baseURL: 'http://localhost:8081/api/v1',
  timeout: 30000
})

export default http
