import { createApp } from 'vue'
import App from '@/app/App.vue'
import { router } from '@/app/router'
import '@/shared/styles/tokens.css'
import '@/shared/styles/base.css'
import '@/shared/styles/utilities.css'

createApp(App).use(router).mount('#app')
