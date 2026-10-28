import request from './request';
// ==================== 工作台 & 统计 ====================
export function getWorkbench() {
    return request.get('/admin/statistics/workbench');
}
export function getOrderStats() {
    return request.get('/admin/statistics/orderStats');
}
export function getProductTop10() {
    return request.get('/admin/statistics/productTop10');
}
export function getSales(type = 'week') {
    return request.get('/admin/statistics/sales', { params: { type } });
}
export function getRevenue(type = 'month') {
    return request.get('/admin/statistics/revenue', { params: { type } });
}
// ==================== 商品管理 ====================
export function getProductList(params: Record<string, any>) {
    return request.get('/admin/product/list', { params });
}
export function addProduct(data: Record<string, any>) {
    return request.post('/admin/product/add', data);
}
export function updateProduct(data: Record<string, any>) {
    return request.put('/admin/product/update', data);
}
export function updateProductStatus(id: number, status: number) {
    return request.put(`/admin/product/status/${id}`, { status });
}
// ==================== 分类管理 ====================
export function getCategoryList() {
    return request.get('/admin/product/category/list');
}
export function addCategory(data: Record<string, any>) {
    return request.post('/admin/product/category/add', data);
}
export function updateCategory(data: Record<string, any>) {
    return request.put('/admin/product/category/update', data);
}
// ==================== 订单管理 ====================
export function getOrderList(params: Record<string, any>) {
    return request.get('/admin/order/list', { params });
}
export function getOrderDetail(orderId: number) {
    return request.get(`/admin/order/detail/${orderId}`);
}
export function updateOrderPrice(orderId: number, totalAmount: number) {
    return request.put(`/admin/order/price/${orderId}`, { totalAmount });
}
export function shipOrder(orderId: number, trackingNo: string) {
    return request.put(`/admin/order/ship/${orderId}`, { trackingNo });
}
export function handleRefund(orderId: number, approve: boolean) {
    return request.put(`/admin/order/refund/${orderId}`, { approve });
}
// ==================== 用户管理 ====================
export function getUserList(params: Record<string, any>) {
    return request.get('/admin/user/list', { params });
}
export function getUserDetail(userId: number) {
    return request.get(`/admin/user/detail/${userId}`);
}
export function updateUserStatus(userId: number, status: number) {
    return request.put(`/admin/user/status/${userId}`, { status });
}
export function updateUserScore(userId: number, score: number) {
    return request.put(`/admin/user/score/${userId}`, { score });
}
// ==================== 角色权限 ====================
export function getRoleList() {
    return request.get('/admin/role/list');
}
export function addRole(data: Record<string, any>) {
    return request.post('/admin/role/add', data);
}
export function updateRole(data: Record<string, any>) {
    return request.put('/admin/role/update', data);
}
export function deleteRole(id: number, version: number) {
    return request.delete(`/admin/role/${id}`, { params: { version } });
}
export function getPermissionTree() {
    return request.get('/admin/permission/tree');
}
export function getRolePermissions(roleId: number) {
    return request.get(`/admin/role/${roleId}/permissions`);
}
export function assignRolePermissions(roleId: number, permissionIds: number[], version: number) {
    return request.post('/admin/role/permission', { roleId, permissionIds, version });
}
// ==================== 管理员 ====================
export function getAdminProfile() {
    return request.get('/admin/profile');
}
export function updateAdminPassword(oldPassword: string, newPassword: string) {
    return request.put('/admin/password', { oldPassword, newPassword });
}
// 列表沿用订单字段，详情在其上补充已存商品快照。
export interface AdminOrder {
    id: number;
    orderNo: string;
    userId: number;
    totalAmount: number | string;
    payStatus: number;
    shipStatus: number;
    receiverName?: string;
    receiverPhone?: string;
    receiverAddressSnapshot?: string;
    addressSnapshotSource?: string;
    trackingNo?: string;
    remark?: string;
    createTime: string;
    payTime?: string;
    shipTime?: string;
}
export interface AdminOrderItem {
    id: number;
    productId: number;
    productName?: string;
    productImg?: string | null;
    skuCode?: string | null;
    skuSpecSnapshot?: string | null;
    skuSnapshotSource?: string;
    quantity: number;
    unitPrice: number | string;
    totalPrice: number | string;
}
export interface AdminOrderDetail extends AdminOrder {
    items?: AdminOrderItem[];
}
export interface StockMovement {
    id: number;
    skuId: number;
    kind: string;
    delta: number;
    stockBefore: number;
    stockAfter: number;
    actorId?: number;
    actorDomain?: string;
    createTime: string;
    orderId?: number;
}
export function getAdminProductDetail(id: number) {
    return request.get(`/admin/product/detail/${id}`);
}
export function getStockMovements(productId: number, params: {
    skuId?: number;
    page: number;
    pageSize: number;
}) {
    return request.get(`/admin/product/${productId}/stock-movements`, { params });
}
