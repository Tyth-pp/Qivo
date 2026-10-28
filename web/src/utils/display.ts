export function price(value: unknown): string {
    const amount = Number(value);
    return value === null || value === undefined || !Number.isFinite(amount) ? '—' : amount.toFixed(2);
}
export function orderStatus(order: {
    payStatus?: number;
    shipStatus?: number;
}) {
    if (order.payStatus === 3)
        return '已取消';
    if (order.payStatus === 2)
        return '已退款';
    if (order.payStatus === 0)
        return '待付款';
    if (order.payStatus === 1)
        return order.shipStatus === 2 ? '已完成' : order.shipStatus === 1 ? '已发货' : '待发货';
    return '状态未知';
}
export const paymentTabs = [{ label: '全部', value: '' }, { label: '待付款', value: '0' }, { label: '已付款', value: '1' }, { label: '已退款', value: '2' }, { label: '已取消', value: '3' }];
export async function copyText(value: string) {
    const { ElMessage } = await import('element-plus');
    try {
        await navigator.clipboard.writeText(value);
        ElMessage.success('已复制');
    }
    catch {
        ElMessage.warning('复制失败，请手动选择编号复制');
    }
}
