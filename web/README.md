# Qivo 前端

Vue 3、TypeScript、Element Plus、Pinia，包含消费者商城和管理员工作台，按桌面浏览器设计。

```powershell
npm ci
npm run dev
npm run build
npm run test:unit
```

后端配置与启动见[根目录说明](../README.md)。Vite默认代理/api与/upload。权限以服务端当前有效摘要为准，不根据角色名称硬编码。

浏览器回归脚本位于scripts/，固定测试素材位于test-fixtures/，不依赖未提交的历史原型。需要Playwright及对应浏览器，可用QIVO_PLAYWRIGHT_MODULE指定本机模块。全站和商品专项分别运行npm run test:ui、npm run test:catalog-ui，先完成生产构建。截图与记录默认写入被忽略的本地docs目录，不作为源码提交。
