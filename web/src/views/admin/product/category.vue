<script setup lang="ts">
import { useAdminAccessStore } from "@/store/adminAccess";
const access = useAdminAccessStore();
import { categoryTree } from '@/utils/category';
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getCategoryList, addCategory, updateCategory } from '@/api/admin';
import { useLoad } from '@/composables/useLoad';
import PageState from '@/components/PageState.vue';
const rows = ref<any[]>([]), visible = ref(false), saving = ref(false), original = ref(''), form = reactive<any>({}), { loading, error, run } = useLoad();
function load() {
    return run(() => getCategoryList(), (r: any) => {
        rows.value = categoryTree(r.data || []);
    });
}
function open(row?: any, parentId = 0) {
    if (!access.can("product:update")) return;
    Object.assign(form, { id: null, name: '', parentId, sortOrder: 0, icon: '', status: 1 }, row || {});
    original.value = JSON.stringify(form);
    visible.value = true;
}
const parentName = computed(() => {
    const find = (xs: any[]): any => {
        for (const x of xs) {
            if (x.id === form.parentId)
                return x.name;
            const n = find(x.children || []);
            if (n)
                return n;
        }
    };
    return form.parentId ? find(rows.value) || `分类 ${form.parentId}` : '顶级分类';
});
async function close(done?: () => void) {
    if (saving.value)
        return;
    if (JSON.stringify(form) !== original.value) {
        try {
            await ElMessageBox.confirm('分类尚未保存，确定关闭吗？', '放弃修改');
        }
        catch {
            return;
        }
    }
    visible.value = false;
    done?.();
}
async function save() {
    if (!access.can("product:update")) return;
    if (saving.value)
        return;
    if (!form.name.trim()) {
        ElMessage.warning('请填写分类名称');
        return;
    }
    saving.value = true;
    try {
        if (form.id)
            await updateCategory(form);
        else
            await addCategory(form);
        visible.value = false;
        ElMessage.success('分类已保存');
        await load();
    }
    catch {
    }
    finally {
        saving.value = false;
    }
}
onMounted(load);
</script>

<template>
  <div class="page-heading">
    <h1>分类管理</h1>
    <el-button v-if="access.can('product:update')" type="primary" @click="open()">＋ 添加分类</el-button>
  </div>
  <section class="panel list-panel">
    <PageState :loading="loading" :error="error" @retry="load">
      <el-table :data="rows" row-key="id" default-expand-all empty-text="还没有商品分类">
        <el-table-column prop="name" label="分类名称" min-width="260" />
        <el-table-column prop="id" label="分类编号" width="130" />
        <el-table-column prop="sortOrder" label="排序" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{row}">
            <span class="status-tag" :class="{gray:row.status!==1}">{{row.status===1?'显示':'隐藏'}}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210">
          <template #default="{row}">
            <div class="row-actions">
              <el-button v-if="access.can('product:update')" link @click="open(row)">编辑</el-button>
              <el-button v-if="access.can('product:update')" link @click="open(undefined,row.id)">添加子分类</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </PageState>
  </section>
  <el-drawer
    v-model="visible"
    :title="form.id?'编辑分类':'添加分类'"
    size="460px"
    :before-close="close"
    :close-on-click-modal="false"
  >
    <el-form label-position="top">
      <el-form-item label="所属分类">
        <el-input :model-value="parentName" disabled />
      </el-form-item>
      <el-form-item label="分类名称">
        <el-input v-model="form.name" maxlength="50" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :precision="0" />
      </el-form-item>
      <el-form-item label="分类图标地址（选填）">
        <el-input v-model="form.icon" placeholder="填写可访问的图片地址" />
      </el-form-item>
      <el-form-item label="显示状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="显示" inactive-text="隐藏" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="close()">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!access.can('product:update')" @click="save">保存分类</el-button>
    </template>
  </el-drawer>
</template>
