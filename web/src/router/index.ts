import { createRouter, createWebHistory } from 'vue-router';
const consumerAuthPage = () => import('@/views/fore/user/auth.vue');
const routes = [
    // ==================== 前台 ====================
    {
        path: '/',
        name: 'index',
        component: () => import('@/views/fore/index.vue')
    },
    {
        path: '/login',
        name: 'login',
        component: consumerAuthPage
    },
    {
        path: '/register',
        name: 'register',
        component: consumerAuthPage
    },
    {
        path: '/about',
        name: 'about',
        component: () => import('@/views/fore/about.vue')
    },
    {
        path: '/product/list',
        name: 'productList',
        component: () => import('@/views/fore/product/list.vue')
    },
    {
        path: '/product/:id',
        name: 'productDetail',
        component: () => import('@/views/fore/product/detail.vue')
    },
    {
        path: '/cart',
        name: 'cart',
        meta: { requiresAuth: true },
        component: () => import('@/views/fore/cart.vue')
    },
    {
        path: '/order/confirm',
        name: 'orderConfirm',
        meta: { requiresAuth: true },
        component: () => import('@/views/fore/order/confirm.vue')
    },
    {
        path: '/order/list',
        name: 'orderList',
        meta: { requiresAuth: true },
        component: () => import('@/views/fore/order/list.vue')
    },
    {
        path: '/user/profile',
        name: 'userProfile',
        meta: { requiresAuth: true },
        component: () => import('@/views/fore/user/profile.vue')
    },
    // ==================== 后台 ====================
    {
        path: '/admin/login',
        name: 'adminLogin',
        component: () => import('@/views/admin/login.vue')
    },
    {
        path: '/admin',
        component: () => import('@/views/admin/layout.vue'),
        meta: { requiresAuth: true },
        children: [
            { path: '', name: 'adminHome', component: () => import('@/views/admin/access.vue') },
            { path: 'access', name: 'adminAccess', component: () => import('@/views/admin/access.vue') },
            { path: 'dashboard', name: 'dashboard', component: () => import('@/views/admin/dashboard.vue') },
            { path: 'statistics', name: 'statistics', component: () => import('@/views/admin/statistics.vue') },
            { path: 'product/list', name: 'adminProduct', component: () => import('@/views/admin/product/list.vue') },
            { path: 'product/category', name: 'adminCategory', component: () => import('@/views/admin/product/category.vue') },
            { path: 'order/list', name: 'adminOrder', component: () => import('@/views/admin/order/list.vue') },
            { path: 'user/list', name: 'adminUser', component: () => import('@/views/admin/system/user.vue') },
            { path: 'role/list', name: 'adminRole', component: () => import('@/views/admin/system/role.vue') },
            { path: 'admin/list', name: 'adminAccounts', component: () => import('@/views/admin/system/admin.vue') },
            { path: 'after-sale/list', name: 'adminAfterSales', component: () => import('@/views/admin/order/afterSale.vue') },
            { path: ':pathMatch(.*)*', redirect: '/admin/access?reason=forbidden' }
        ]
    }
];
const router = createRouter({
    history: createWebHistory(),
    scrollBehavior(to, from, saved) {
        if (to.path.startsWith('/admin') || (to.path === from.path && to.path === '/user/profile'))
            return false;
        return saved || { top: 0 };
    },
    routes
});
const titles: Record<string, string> = {
    index: '首页', login: '登录', register: '注册', about: '关于栖物', productList: '全部商品', productDetail: '商品详情', cart: '购物车', orderConfirm: '确认订单', orderList: '我的订单', userProfile: '个人中心', adminLogin: '管理员登录', dashboard: '工作台', statistics: '数据统计', adminProduct: '商品管理', adminCategory: '分类管理', adminOrder: '订单管理', adminUser: '用户管理', adminRole: '角色权限', adminAccounts: '管理员账号', adminAfterSales: '售后处理'
};
router.afterEach(to => {
    document.title = `${titles[String(to.name)] || '栖物'} · 栖物 Qivo`;
});
export default router;
