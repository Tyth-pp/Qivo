<script setup lang="ts">
import { useAdminAccessStore } from "@/store/adminAccess";
const access = useAdminAccessStore();
import { categoryTree } from '@/utils/category';
import { onBeforeRouteLeave } from 'vue-router';
import PageState from '@/components/PageState.vue';
import ProductImage from '@/components/ProductImage.vue';
import { useLoad } from '@/composables/useLoad';
import { getAdminProductDetail, getStockMovements } from '@/api/admin';
import type { StockMovement } from '@/api/admin';
import { price, copyText } from '@/utils/display';
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getProductList, addProduct, updateProduct, updateProductStatus, getCategoryList } from '@/api/admin';
import request from '@/api/request';
import ProductImageInput from '@/components/ProductImageInput.vue';
import { validImageAddress } from '@/utils/image';
import { combinations, specText, parseSelections, specKey } from '@/utils/sku';
const tableData = ref<any[]>([]), categories = ref<any[]>([]), total = ref(0);
const categoryLabels = computed(() => {
    const labels = new Map<number, string>();
    const collect = (nodes: any[]) => nodes.forEach(node => {
        labels.set(node.id, node.name);
        collect(node.children || []);
    });
    collect(categories.value);
    return labels;
});
const search = reactive({ page: 1, pageSize: 10, keyword: '', status: null as number | null, categoryId: null as number | null });
const visible = ref(false), saving = ref(false);
const form = reactive<any>({ id: null, name: '', description: '', categoryId: null, mainImage: '', specMode: 'SINGLE', specDefinitions: [], skus: [] });
const uploads = ref(0);
const skuBusy = reactive<Record<number, boolean>>({});
function uploading(active: boolean) {
    uploads.value += active ? 1 : -1;
}
const existing = ref<any[]>([]);
const newId = () => 's' + crypto.randomUUID().replaceAll('-', '').slice(0, 24);
const draft = (specValues: any[]) => ({ specValues, price: 1, originalPrice: null, stock: 0, status: 'ENABLED', image: '' });
const { loading, error, run } = useLoad();
const editorTab = ref('basic'), originalForm = ref(''), originalSkus = ref<Record<number, string>>({});
const snapshot = (row: any) => JSON.stringify({ price: row.price, originalPrice: row.originalPrice, status: row.status, image: row.image });
const dirty = computed(() => visible.value && (JSON.stringify(form) !== originalForm.value || existing.value.some(s => snapshot(s) !== originalSkus.value[s.id])));
function baseline() {
    originalForm.value = JSON.stringify(form);
    originalSkus.value = Object.fromEntries(existing.value.map(s => [s.id, snapshot(s)]));
}
async function canClose() {
    if (uploads.value || saving.value || stockSaving.value || Object.values(skuBusy).some(Boolean)) {
        ElMessage.warning('请等待上传或保存完成');
        return false;
    }
    if (!dirty.value)
        return true;
    try {
        await ElMessageBox.confirm('还有未保存的商品或规格修改，确定关闭吗？', '放弃修改');
        return true;
    }
    catch {
        return false;
    }
}
async function closeEditor(done?: () => void) {
    if (await canClose()) {
        visible.value = false;
        done?.();
    }
}
onBeforeRouteLeave(canClose);
function load() {
    return run(() => getProductList(search), (r: any) => {
        tableData.value = r.data?.records || [];
        total.value = r.data?.total || 0;
    });
}
async function loadPage() {
    loading.value = true;
    error.value = false;
    try {
        const c: any = access.can("category:list") ? await getCategoryList() : { data: [] };
        categories.value = categoryTree(c.data || []);
        await load();
    }
    catch (failure) {
        error.value = (failure as { response?: { status?: number } })?.response?.status === 403 ? 'forbidden' : true;
        loading.value = false;
    }
}
const expanded = ref<Record<number, any>>({}), expandErrors = ref<Record<number, boolean>>({});
async function expand(row: any, rows: any[]) {
    if (!rows.some(x => x.id === row.id) || expanded.value[row.id])
        return;
    expandErrors.value[row.id] = false;
    try {
        const r: any = await getAdminProductDetail(row.id);
        expanded.value[row.id] = r.data;
    }
    catch {
        expandErrors.value[row.id] = true;
    }
}
function openAdd() {
    if (!access.can("product:add")) return;
    Object.assign(form, { id: null, name: '', description: '', categoryId: null, mainImage: '', specMode: 'SINGLE', specDefinitions: [], skus: [draft([])] });
    existing.value = [];
    editorTab.value = 'basic';
    baseline();
    visible.value = true;
}
async function openEdit(row: any) {
    if (!access.can("product:update")) return;
    try {
        const r: any = await getAdminProductDetail(row.id);
        Object.assign(form, r.data, { skus: [], specDefinitions: structuredClone(r.data.specDefinitions || []) });
        existing.value = (r.data.skus || []).map((item: any) => ({ ...item, specValues: parseSelections(item.specValues) }));
        editorTab.value = 'basic';
        baseline();
        visible.value = true;
    }
    catch { /* 请求层提示读取失败，允许再次点击编辑。 */
    }
}
function changeMode() {
    form.specDefinitions = [];
    form.skus = form.specMode === 'SINGLE' ? [draft([])] : [];
}
function addDimension() {
    if (form.specDefinitions.length < 3)
        form.specDefinitions.push({ id: newId(), name: '', values: [{ id: newId(), name: '' }] });
}
function generate() {
    try {
        const keys = new Set(existing.value.map(s => specKey(s.specValues)));
        const old = new Map(form.skus.map((s: any) => [specKey(s.specValues), s]));
        form.skus = combinations(form.specDefinitions).filter(s => !keys.has(specKey(s))).map(s => old.get(specKey(s)) || draft(s));
    }
    catch (e: any) {
        ElMessage.warning(e.message);
    }
}
async function save() {
    if (!access.can(form.id ? "product:update" : "product:add")) return;
    if (existing.value.some(row => snapshot(row) !== originalSkus.value[row.id])) {
        editorTab.value = 'specs';
        ElMessage.warning('请先保存已修改的规格，再保存商品信息');
        return;
    }
    if (saving.value || uploads.value)
        return;
    if (![form.mainImage, ...form.skus.map((s: any) => s.image)].every(v => validImageAddress(v || ''))) {
        ElMessage.warning('请上传图片或填写有效图片访问地址');
        return;
    }
    saving.value = true;
    try {
        const payload = {
            id: form.id, name: form.name, description: form.description, categoryId: form.categoryId, mainImage: form.mainImage, specMode: form.specMode, specDefinitions: form.specDefinitions, skus: form.skus
        };
        if (form.id)
            await updateProduct(payload);
        else
            await addProduct(payload);
        ElMessage.success(form.id ? (form.skus.length ? '商品信息与新增组合已保存' : '商品信息已保存') : '新商品已保存，默认下架，请确认规格后上架');
        visible.value = false;
        expanded.value = {};
        await load();
    }
    catch { /* 保留表单，接口错误由请求拦截器提示。 */
    }
    finally {
        saving.value = false;
    }
}
async function saveSku(s: any) {
    if (!access.can("product:update")) return;
    if (skuBusy[s.id] || uploads.value)
        return;
    if (!validImageAddress(s.image || '')) {
        ElMessage.warning('请填写有效的规格图片地址');
        return;
    }
    skuBusy[s.id] = true;
    try {
        await request.put(`/admin/product/${form.id}/sku/${s.id}`, { price: s.price, originalPrice: s.originalPrice, status: s.status, image: s.image });
        ElMessage.success('规格信息已保存');
        originalSkus.value[s.id] = snapshot(s);
        delete expanded.value[form.id];
        await load();
    }
    catch { /* 请求错误由拦截器显示，保留编辑内容。 */
    }
    finally {
        skuBusy[s.id] = false;
    }
}
async function refreshSkus() {
    const r: any = await request.get(`/admin/product/detail/${form.id}`);
    for (const row of existing.value) {
        const latest = r.data.skus.find((s: any) => s.id === row.id);
        if (latest)
            row.stock = latest.stock;
    }
}
const stockVisible = ref(false), stockRow = ref<any>({}), delta = ref(1), stockSaving = ref(false);
const nextStock = computed(() => Number(stockRow.value.stock || 0) + Number(delta.value));
const validDelta = computed(() => Number.isInteger(delta.value) && delta.value !== 0 && Math.abs(delta.value) <= 2147483647 && nextStock.value >= 0 && nextStock.value <= 2147483647);
function adjust(row: any) {
    if (!access.can("product:update")) return;
    stockRow.value = row;
    delta.value = 1;
    stockVisible.value = true;
}
async function saveStock() {
    if (!access.can("product:update")) return;
    if (!validDelta.value || stockSaving.value)
        return;
    stockSaving.value = true;
    try {
        await request.post(`/admin/product/${form.id}/sku/${stockRow.value.id}/stock`, { delta: delta.value });
        stockVisible.value = false;
        ElMessage.success('库存已调整');
        delete expanded.value[form.id];
        try {
            await refreshSkus();
        }
        catch {
            ElMessage.warning('库存已调整，数据刷新失败，请重新打开商品核对库存');
        }
        await load();
    }
    catch {
    }
    finally {
        stockSaving.value = false;
    }
}
const ledgerVisible = ref(false), ledgerRows = ref<StockMovement[]>([]), ledgerPage = ref(1), ledgerTotal = ref(0), ledgerSku = ref<number>(), ledgerProduct = ref<any>({});
const ledgerLoad = useLoad();
function loadLedger() {
    return ledgerLoad.run(() => getStockMovements(ledgerProduct.value.id, { page: ledgerPage.value, pageSize: 10, skuId: ledgerSku.value }), (r: any) => {
        ledgerRows.value = r.data?.records || [];
        ledgerTotal.value = r.data?.total || 0;
    });
}
function ledger(row: any) {
    ledgerProduct.value = row;
    ledgerSku.value = undefined;
    ledgerPage.value = 1;
    ledgerVisible.value = true;
    loadLedger();
}
const movementNames: Record<string, string> = { INITIAL: '初始库存', ORDER_RESERVE: '下单扣减', MANUAL_ADJUST: '手动调整', ORDER_RELEASE: '订单回补', REFUND_RELEASE: '退款回补' };
async function status(row: any) {
    if (!access.can("product:update")) return;
    try {
        await ElMessageBox.confirm(`确定${row.status === 1 ? '下架' : '上架'}该商品吗？`, '修改销售状态');
        await updateProductStatus(row.id, row.status === 1 ? 0 : 1);
        await load();
    }
    catch {
    }
}
onMounted(() => loadPage().catch(() => {
    error.value = true;
    loading.value = false;
}));
</script>

<template>
  <div class="page-heading">
    <h1>商品管理</h1>
    <el-button v-if="access.can('product:add')" :disabled="!access.can('category:list')" type="primary" @click="openAdd">＋ 添加商品</el-button>
  </div>
  <section class="panel list-panel">
    <el-tabs
      :model-value="search.status===null?'all':String(search.status)"
      @update:model-value="(v:any)=>{search.status=v==='all'?null:Number(v);search.page=1;load()}"
    >
      <el-tab-pane label="全部商品" name="all" />
      <el-tab-pane label="在售商品" name="1" />
      <el-tab-pane label="已下架" name="0" />
    </el-tabs>
    <form class="filter-row" @submit.prevent="search.page=1;load()">
      <el-input v-model="search.keyword" placeholder="搜索商品名称" clearable aria-label="商品名称" />
      <el-tree-select
        v-if="access.can('category:list')"
        v-model="search.categoryId"
        :data="categories"
        :props="{label:'name',value:'id',children:'children'}"
        node-key="id"
        check-strictly
        clearable
        placeholder="全部分类"
       />
      <el-button native-type="submit">查询</el-button>
      <el-button link @click="Object.assign(search,{keyword:'',categoryId:null,status:null,page:1});load()">重置</el-button>
      <span class="filter-count">共 {{total}} 件商品</span>
    </form>
    <PageState :loading="loading" :error="error" @retry="loadPage">
      <el-table :data="tableData" row-key="id" @expand-change="expand" empty-text="没有符合条件的商品">
        <el-table-column type="expand" width="40">
          <template #default="{row}">
            <div class="spec-preview">
              <PageState
                :loading="!expanded[row.id]&&!expandErrors[row.id]"
                :error="expandErrors[row.id]"
                @retry="expand(row,[row])"
              >
                <el-table :data="expanded[row.id]?.skus||[]" size="small">
                  <el-table-column label="规格" min-width="240">
                    <template #default="{row:s}">{{specText(expanded[row.id].specDefinitions||[],parseSelections(s.specValues))}}</template>
                  </el-table-column>
                  <el-table-column prop="id" label="规格编号" width="110" />
                  <el-table-column label="售价" width="100">
                    <template #default="{row:s}">¥{{price(s.price)}}</template>
                  </el-table-column>
                  <el-table-column prop="stock" label="库存" width="100" />
                  <el-table-column label="状态" width="100">
                    <template #default="{row:s}">{{s.status==='ENABLED'?'可售':'已停用'}}</template>
                  </el-table-column>
                </el-table>
              </PageState>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="260">
          <template #default="{row}">
            <div class="cell-product">
              <ProductImage :src="row.mainImage" :alt="row.name" />
              <div>
                <strong>{{row.name}}</strong>
                <small class="copy-line">
                  商品编号
                  {{row.id}}
                  <button class="copy-btn" aria-label="复制商品编号" @click="copyText(String(row.id))">⧉</button>
                </small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="access.can('category:list')" label="分类" min-width="110">
          <template #default="{row}"><span class="muted">{{categoryLabels.get(row.categoryId)||'未记录'}}</span></template>
        </el-table-column>
        <el-table-column label="售价 / 元" min-width="130">
          <template #default="{row}">
            <span class="mono">{{price(row.price)}}{{Number(row.maxPrice)>Number(row.price)?` – ${price(row.maxPrice)}`:''}}</span>
          </template>
        </el-table-column>
        <el-table-column label="可售库存" width="100">
          <template #default="{row}">{{row.totalStock??row.stock??0}}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <span class="status-tag" :class="{gray:row.status!==1}">{{row.status===1?'在售':'已下架'}}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{row}">
            <div class="row-actions">
              <el-button v-if="access.can('product:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link @click="ledger(row)">库存记录</el-button>
              <el-button v-if="access.can('product:update')" link @click="status(row)">{{row.status===1?'下架':'上架'}}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="search.page"
          v-model:page-size="search.pageSize"
          :total="total"
          :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next"
          @change="load"
         />
      </div>
    </PageState>
  </section>
  <el-drawer
    v-model="visible"
    :title="form.id?'编辑商品':'添加商品'"
    size="760px"
    :before-close="closeEditor"
    :close-on-click-modal="false"
    :close-on-press-escape="!uploads&&!saving"
    :show-close="!uploads&&!saving"
  >
    <el-tabs v-model="editorTab">
      <el-tab-pane label="基本信息" name="basic" />
      <el-tab-pane label="商品规格" name="specs" />
    </el-tabs>
    <el-form v-show="editorTab==='basic'" label-position="top">
      <el-form-item label="商品名称">
        <el-input v-model="form.name" maxlength="100" />
      </el-form-item>
      <el-form-item label="商品分类">
        <el-tree-select
          :disabled="!access.can('category:list')"
          v-model="form.categoryId"
          :data="categories"
          :props="{label:'name',value:'id'}"
          node-key="id"
          check-strictly
          style="width:100%"
         />
      </el-form-item>
      <el-form-item label="商品简介">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="商品主图">
        <ProductImageInput v-model="form.mainImage" :disabled="saving||!access.can(form.id?'product:update':'product:add')" @uploading="uploading" />
      </el-form-item>
    </el-form>
    <div v-show="editorTab==='specs'">
      <div class="soft-note">商品信息、已有规格、库存分别保存。库存调整后会保留其他未保存内容。</div>
      <details :open="!form.id" v-if="!form.id || form.specMode==='MULTI'" class="spec-options">
        <summary>规格选项与新增组合</summary>
        <el-form label-position="top">
          <el-form-item label="规格类型">
            <el-radio-group v-model="form.specMode" :disabled="!!form.id" @change="changeMode">
              <el-radio value="SINGLE">单一规格</el-radio>
              <el-radio value="MULTI">多种规格</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <div v-if="form.specMode==='MULTI'" class="spec-definition">
          <div v-for="(d,di) in form.specDefinitions" :key="d.id" class="spec-dimension">
            <div class="inline-actions">
              <el-input v-model="d.name" placeholder="规格名称，例如颜色" style="width:170px" />
              <el-button v-if="!form.id" link class="danger-link" @click="form.specDefinitions.splice(di,1)">移除</el-button>
            </div>
            <div class="inline-actions" style="margin-top:10px;flex-wrap:wrap">
              <div class="inline-actions" v-for="(v,vi) in d.values" :key="v.id" style="gap:5px">
                <el-input v-model="v.name" placeholder="选项，例如暖白" style="width:130px" />
                <el-button v-if="!form.id" link @click="d.values.splice(vi,1)">×</el-button>
              </div>
              <el-button @click="d.values.push({id:newId(),name:''})">添加选项</el-button>
            </div>
          </div>
          <div class="inline-actions" style="margin:15px 0">
            <el-button :disabled="form.specDefinitions.length>=3||!!form.id" @click="addDimension">添加规格维度</el-button>
            <el-button @click="generate">生成新增组合</el-button>
          </div>
        </div>
      </details>
      <h3 v-if="existing.length" style="margin:22px 0 14px">已有规格</h3>
      <section v-for="row in existing" :key="row.id" class="spec-editor">
        <header>
          {{specText(form.specDefinitions, row.specValues)}}
          <small>规格编号 {{row.id}}</small>
        </header>
        <el-form label-position="top" class="form-grid">
          <el-form-item label="售价 / 元">
            <el-input-number v-model="row.price" :precision="2" :min="0.01" />
          </el-form-item>
          <el-form-item label="原价（选填）">
            <el-input-number v-model="row.originalPrice" :precision="2" :min="0" />
          </el-form-item>
          <el-form-item label="销售状态">
            <el-select v-model="row.status">
              <el-option value="ENABLED" label="可售" />
              <el-option value="DISABLED" label="停用" />
            </el-select>
          </el-form-item>
          <el-form-item label="当前库存">
            <div class="inline-actions">
              <span>{{row.stock}}</span>
              <el-button v-if="access.can('product:update')" link @click="adjust(row)">调整库存 →</el-button>
            </div>
          </el-form-item>
          <el-form-item label="规格图片（选填）" class="full">
            <ProductImageInput v-model="row.image" :disabled="skuBusy[row.id]||!access.can('product:update')" @uploading="uploading" />
          </el-form-item>
        </el-form>
        <el-button :loading="skuBusy[row.id]" :disabled="uploads>0||saving||!access.can('product:update')" @click="saveSku(row)">保存该规格</el-button>
      </section>
      <h3 v-if="form.skus.length" style="margin:22px 0 14px">{{form.id?'新增组合':'初始规格'}}</h3>
      <section v-for="(row,i) in form.skus" :key="specKey(row.specValues)" class="spec-editor">
        <header>
          {{specText(form.specDefinitions,row.specValues)}}
          <el-button v-if="form.specMode==='MULTI'" link @click="form.skus.splice(i,1)">移除</el-button>
        </header>
        <el-form label-position="top" class="form-grid">
          <el-form-item label="售价 / 元">
            <el-input-number v-model="row.price" :min="0.01" :precision="2" />
          </el-form-item>
          <el-form-item label="初始库存">
            <el-input-number v-model="row.stock" :min="0" :precision="0" />
          </el-form-item>
          <el-form-item label="原价（选填）">
            <el-input-number v-model="row.originalPrice" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="销售状态">
            <el-select v-model="row.status">
              <el-option label="可售" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格图片（选填）" class="full">
            <ProductImageInput v-model="row.image" :disabled="saving||!access.can(form.id?'product:update':'product:add')" @uploading="uploading" />
          </el-form-item>
        </el-form>
      </section>
    </div>
    <template #footer>
      <el-button :disabled="uploads>0||saving" @click="closeEditor()">关闭</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="uploads>0||Object.values(skuBusy).some(Boolean)||!access.can(form.id?'product:update':'product:add')"
        @click="save"
      >{{form.skus.length?'保存商品与新增组合':'保存商品信息'}}</el-button>
    </template>
  </el-drawer>
  <el-dialog
    v-model="stockVisible"
    title="调整库存"
    width="440px"
    :close-on-click-modal="false"
    :show-close="!stockSaving"
    :close-on-press-escape="!stockSaving"
  >
    <p>{{specText(form.specDefinitions,stockRow.specValues||[])}}</p>
    <div class="soft-note">当前库存 {{stockRow.stock}} 件</div>
    <el-form label-position="top">
      <el-form-item label="增加或减少的数量">
        <el-input-number v-model="delta" :precision="0" />
      </el-form-item>
    </el-form>
    <p>
      调整后库存：
      <strong>{{nextStock}}</strong>
      件
    </p>
    <small>正数增加，负数减少；不能小于 0。</small>
    <template #footer>
      <el-button :disabled="stockSaving" @click="stockVisible=false">取消</el-button>
      <el-button type="primary" :loading="stockSaving" :disabled="!validDelta||!access.can('product:update')" @click="saveStock">确认调整</el-button>
    </template>
  </el-dialog>
  <el-drawer v-model="ledgerVisible" title="库存变动记录" size="780px">
    <div class="drawer-subject">
      <ProductImage :src="ledgerProduct.mainImage" :alt="ledgerProduct.name" />
      <div>
        <h3>{{ledgerProduct.name}}</h3>
        <small>商品编号 {{ledgerProduct.id}}</small>
      </div>
    </div>
    <PageState
      :loading="ledgerLoad.loading.value"
      :error="ledgerLoad.error.value==='forbidden'?'forbidden':ledgerLoad.error.value?'库存记录暂不可用，请稍后重试。':false"
      @retry="loadLedger"
    >
      <el-table :data="ledgerRows" empty-text="暂无库存变动记录">
        <el-table-column prop="createTime" label="时间" min-width="165" />
        <el-table-column prop="skuId" label="规格编号" width="95" />
        <el-table-column label="原因" min-width="100">
          <template #default="{row}">{{movementNames[row.kind]||'库存变动'}}</template>
        </el-table-column>
        <el-table-column label="变化" width="85">
          <template #default="{row}">{{row.delta>0?'+':''}}{{row.delta}}</template>
        </el-table-column>
        <el-table-column label="调整前 → 后" min-width="110">
          <template #default="{row}">{{row.stockBefore}} → {{row.stockAfter}}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="ledgerPage"
          :total="ledgerTotal"
          :page-size="10"
          layout="total,prev,pager,next"
          @current-change="loadLedger"
         />
      </div>
    </PageState>
  </el-drawer>
</template>

<style scoped>
.spec-options {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 13px 16px;
  margin: 16px 0;
}

.spec-options summary {
  color: var(--green);
  cursor: pointer;
  font-size: 12px;
}

.spec-options[open] summary {
  margin-bottom: 20px;
}

.spec-editor {
  border: 1px solid var(--line);
  padding: 17px;
  border-radius: 7px;
  margin-bottom: 17px;
}

.spec-editor header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  font-size: 13px;
}

.spec-editor .el-input-number {
  width: 100%;
}

.spec-dimension {
  padding: 15px;
  border: 1px solid var(--line);
  margin-bottom: 12px;
  border-radius: 6px;
}
</style>
