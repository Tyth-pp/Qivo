<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getOrderList, getOrderDetail, payOrder, cancelOrder, confirmOrder } from '@/api/order';
import { addComment, appendComment } from '@/api/comment';
import { snapshotText } from '@/utils/sku';
import { price, copyText, paymentTabs } from '@/utils/display';
import { useLoad } from '@/composables/useLoad';
import PageState from './PageState.vue';
import ProductImage from './ProductImage.vue';
import OrderStatus from './OrderStatus.vue';
import AfterSalePanel from './AfterSalePanel.vue';
const afterSaleBusy=ref(false);
const props = defineProps<{
    compact?: boolean;
}>();
const orders = ref<any[]>([]), total = ref(0), page = ref(1), tab = ref(''), busy = ref(false), detail = ref<any>(null);
const comment = ref(false), target = ref<any>(), rating = ref(5), content = ref('');
const { loading, error, run } = useLoad();
async function items(o: any) {
    try {
        const r: any = await getOrderDetail(o.id);
        o.items = r.data?.items || [];
        o.comments = r.data?.comments || [];
        o.itemsError = false;
    }
    catch {
        o.itemsError = true;
    }
}
function load() {
    return run(async () => {
        const r: any = await getOrderList({ page: page.value, pageSize: props.compact ? 3 : 10, status: tab.value === '' ? undefined : Number(tab.value) });
        const rows = r.data?.records || [];
        await Promise.all(rows.map(items));
        return { rows, total: r.data?.total || 0 };
    }, r => {
        orders.value = r.rows;
        total.value = r.total;
    });
}
watch(tab, () => {
    page.value = 1;
    load();
}, { immediate: true });
async function action(o: any, type: string) {
    if (busy.value)
        return;
    busy.value = true;
    try {
        if (type === 'pay') {
            await ElMessageBox.confirm('这是模拟付款，不产生真实扣款。确认继续？', '模拟付款');
            await payOrder(o.id);
        }
        else if (type === 'cancel') {
            await ElMessageBox.confirm('确定取消这笔订单吗？', '取消订单', { type: 'warning' });
            await cancelOrder(o.id);
        }
        else {
            await ElMessageBox.confirm('确认已经收到商品了吗？', '确认收货');
            await confirmOrder(o.id);
        }
        ElMessage.success('操作成功');
        await load();
    }
    catch { /* 取消或错误保留当前页面 */
    }
    finally {
        busy.value = false;
    }
}
function openComment(o: any, i: any) {
    target.value = { o, i, existing: o.comments?.find((c: any) => c.productId === i.productId) };
    content.value = '';
    rating.value = 5;
    comment.value = true;
}
async function submitComment() {
    if (busy.value)
        return;
    if (!content.value.trim()) {
        ElMessage.warning('请填写评价内容');
        return;
    }
    busy.value = true;
    try {
        if (target.value.existing)
            await appendComment(target.value.existing.id, content.value);
        else
            await addComment({ orderId: target.value.o.id, productId: target.value.i.productId, rating: rating.value, content: content.value });
        comment.value = false;
        ElMessage.success('评价已保存');
        await load();
    }
    catch {
    }
    finally {
        busy.value = false;
    }
}
</script>

<template>
  <div class="customer-orders">
    <div class="section-heading" v-if="compact">
      <h1>最近订单</h1>
      <router-link to="/order/list" class="text-link">全部订单 →</router-link>
    </div>
    <el-tabs v-if="!compact" v-model="tab">
      <el-tab-pane v-for="t in paymentTabs" :key="t.value" :label="t.label" :name="t.value" />
    </el-tabs>
    <PageState
      :loading="loading"
      :error="error"
      :empty="!orders.length"
      :title="compact ? '还没有订单' : '当前没有订单'"
      :description="compact ? '下单后，可以在这里查看订单和处理进度。' : '选购喜欢的商品后，会显示在这里。'"
      @retry="load"
    >
      <template #action>
        <router-link to="/product/list" :class="compact ? 'compact-browse-button' : 'text-link'">{{compact ? '浏览商品 →' : '去选购 →'}}</router-link>
      </template>
      <article class="customer-order" v-for="o in orders" :key="o.id">
        <header>
          <div class="order-header-meta">
            <small>{{o.createTime}}</small>
            <span class="mono copy-line">{{o.orderNo}}
              <button class="copy-btn" aria-label="复制订单号" @click="copyText(o.orderNo)">⧉</button>
            </span>
          </div>
          <OrderStatus :order="o" />
        </header>
        <div class="order-body">
        <div class="order-items">
          <div v-if="o.itemsError" class="soft-note">
            商品明细暂未加载成功
            <el-button link @click="items(o)">重试</el-button>
          </div>
          <div class="order-item" v-for="i in o.items" :key="i.id">
            <ProductImage :src="i.productImg" :alt="i.productName" />
            <div>
              <router-link :to="`/product/${i.productId}`">
                <h3>{{i.productName}}</h3>
              </router-link>
              <small>{{snapshotText(i.skuSpecSnapshot)}} × {{i.quantity}}</small>
            </div>
            <span class="mono">¥{{price(i.unitPrice)}}</span>
            <el-button v-if="o.payStatus===1 && o.shipStatus===2" link @click="openComment(o,i)">{{o.comments?.some((c:any)=>c.productId===i.productId)?'追加评价':'评价'}}</el-button>
          </div>
        </div>
          <div class="order-amount"><small>共 {{o.items?.reduce((n:number,i:any)=>n+i.quantity,0)||0}} 件商品</small><strong class="mono">¥{{price(o.totalAmount)}}</strong></div>
          <div class="order-actions">
            <el-button v-if="o.payStatus===1&&o.shipStatus===0" @click="detail=o">售后申请与记录</el-button>
            <el-button v-if="o.payStatus===0" :disabled="busy" @click="action(o,'cancel')">取消订单</el-button>
            <el-button v-if="o.payStatus===0" type="primary" :disabled="busy" @click="action(o,'pay')">模拟付款</el-button>
            <el-button
              v-if="o.payStatus===1 && o.shipStatus===1"
              type="primary"
              :disabled="busy"
              @click="action(o,'receive')"
            >确认收货</el-button>
            <el-button link @click="detail=o">查看详情 →</el-button>
          </div>
        </div>
      </article>
      <div class="pager" v-if="!compact&&total>10">
        <el-pagination
          v-model:current-page="page"
          :page-size="10"
          :total="total"
          layout="total,prev,pager,next"
          @current-change="load"
         />
      </div>
    </PageState>
    <el-drawer class="consumer-drawer" :model-value="!!detail" title="订单详情" size="540px" :close-on-click-modal="!afterSaleBusy" :close-on-press-escape="!afterSaleBusy" :show-close="!afterSaleBusy" :before-close="(done:any)=>{if(!afterSaleBusy)done()}" @close="detail=null">
      <template v-if="detail">
        <OrderStatus :order="detail" />
        <dl class="info-list" style="margin-top:24px">
          <dt>订单编号</dt>
          <dd class="mono">{{detail.orderNo}}</dd>
          <dt>下单时间</dt>
          <dd>{{detail.createTime}}</dd>
          <dt>收货信息</dt>
          <dd>{{detail.addressSnapshotSource==='CURRENT'?`${detail.receiverName} ${detail.receiverPhone}`:'历史订单未记录收货快照'}}</dd>
          <dt>收货地址</dt>
          <dd>{{detail.addressSnapshotSource==='CURRENT'?detail.receiverAddressSnapshot:'—'}}</dd>
          <dt>物流单号</dt>
          <dd>{{detail.trackingNo||'暂未发货'}}</dd>
          <dt>订单备注</dt>
          <dd>{{detail.remark||'无'}}</dd>
          <dt>订单金额</dt>
          <dd class="price">¥{{price(detail.totalAmount)}}</dd>
        </dl>
        <AfterSalePanel :order-id="detail.id" :eligible="detail.payStatus===1&&detail.shipStatus===0" :amount="detail.totalAmount" @busy="afterSaleBusy=$event" @changed="load" />
      </template>
    </el-drawer>
    <el-dialog class="consumer-dialog"
      v-model="comment"
      :title="target?.existing?'追加评价':'商品评价'"
      width="460px"
      :close-on-click-modal="!busy"
      :close-on-press-escape="!busy"
      :show-close="!busy"
    >
      <el-form label-position="top">
        <el-form-item label="评分" v-if="!target?.existing">
          <el-rate v-model="rating" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="content" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy" @click="comment=false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submitComment">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.customer-order {
  border-bottom: 1px solid var(--line);
  padding: 19px 0 23px;
}

.customer-order:last-child {
  border-bottom: 0;
}

.customer-order header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.customer-order header small {
  font-size: 11px;
  display: block;
  margin-top: 5px;
}

.order-item {
  display: flex;
  gap: 15px;
  align-items: center;
  margin: 18px 0;
  font-size: 13px;
}

.order-item .product-image {
  width: 57px;
}

.order-item>div:not(.product-image) {
  flex: 1;
  min-width: 0;
}

.order-item h3 {
  font-size: 13px;
}

.order-item small {
  display: block;
  margin-top: 5px;
  font-size: 11px;
}

.customer-order footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.customer-order footer strong {
  font-size: 16px;
  margin-left: 9px;
}

.customer-order footer .inline-actions {
  gap: 10px;
}

.customer-order footer .el-button+.el-button {
  margin: 0;
}
.compact-browse-button { display:inline-flex;align-items:center;background:#164dff;color:#fff;border-radius:5px;padding:9px 22px;font-size:13px; }
</style>
