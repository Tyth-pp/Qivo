import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setupPermissionGuard } from './router/permission'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import './storefront-theme.css'
import './admin-theme.css'
import { initializeTabSession } from './utils/session'

initializeTabSession()

// 注册路由守卫
setupPermissionGuard(router)

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
