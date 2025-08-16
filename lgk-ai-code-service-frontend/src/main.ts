
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';

import App from './App.vue'
import router from './router'
import { accessDirective } from './access/index'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

// 注册权限指令
app.directive('access', accessDirective)

app.mount('#app')





