<script setup lang="ts">
import { useAdminAccessStore } from "@/store/adminAccess";
const access = useAdminAccessStore();
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getUserList, getUserDetail, updateUserStatus, updateUserScore } from '@/api/admin';
import { useLoad } from '@/composables/useLoad';
import PageState from '@/components/PageState.vue';
const rows = ref<any[]>([]), total = ref(0), search = reactive({ page: 1, pageSize: 10, keyword: '' }), { loading, error, run } = useLoad();
function load() {
    return run(() => getUserList(search), (r: any) => {
        rows.value = r.data?.records || [];
        total.value = r.data?.total || 0;
    });
}
const scoreVisible = ref(false), selected = ref<any>({}), score = ref(0), saving = ref(false), detailVisible = ref(false), detail = ref<any>({}), detailLoad = useLoad(), detailId = ref(0);
function showDetail(row: any) {
    detailId.value = row.id;
    detailVisible.value = true;
    loadDetail();
}
function loadDetail() {
    return detailLoad.run(() => getUserDetail(detailId.value), (r: any) => {
        detail.value = r.data || {};
    });
}
async function toggle(row: any) {
    if (!access.can("user:disable")) return;
    if (saving.value)
        return;
    try {
        await ElMessageBox.confirm(`确定${row.status === 1 ? '禁用' : '启用'}此账号吗？`, '修改账号状态', { type: 'warning' });
        saving.value = true;
        await updateUserStatus(row.id, row.status === 1 ? 0 : 1);
        ElMessage.success('账号状态已更新');
        await load();
    }
    catch {
    }
    finally {
        saving.value = false;
    }
}
function editScore(row: any) {
    if (!access.can("user:disable")) return;
    selected.value = row;
    score.value = Number(row.score || 0);
    scoreVisible.value = true;
}
async function saveScore() {
    if (!access.can("user:disable")) return;
    if (saving.value)
        return;
    if (!Number.isInteger(score.value) || score.value < 0)
        return;
    saving.value = true;
    try {
        await updateUserScore(selected.value.id, score.value);
        scoreVisible.value = false;
        ElMessage.success('积分已保存');
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
    <h1>用户管理</h1>
    <small>账号与基本资料</small>
  </div>
  <section class="panel list-panel">
    <form class="filter-row" @submit.prevent="search.page=1;load()">
      <el-input v-model="search.keyword" placeholder="搜索用户名、手机或邮箱" aria-label="用户关键词" clearable />
      <el-button native-type="submit">查询</el-button>
      <el-button link @click="search.keyword='';search.page=1;load()">重置</el-button>
      <span class="filter-count">共 {{total}} 位用户</span>
    </form>
    <PageState :loading="loading" :error="error" @retry="load">
      <el-table :data="rows" empty-text="没有符合条件的用户">
        <el-table-column prop="username" label="用户名" min-width="150" />
        <el-table-column prop="id" label="用户编号" width="100" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="190" />
        <el-table-column prop="score" label="积分" width="95" />
        <el-table-column label="账号状态" width="105">
          <template #default="{row}">
            <span class="status-tag" :class="{gray:row.status!==1}">{{row.status===1?'正常':'已禁用'}}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{row}">
            <div class="row-actions">
              <el-button link @click="showDetail(row)">详情</el-button>
              <el-button v-if="access.can('user:disable')" link @click="editScore(row)">改积分</el-button>
              <el-button v-if="access.can('user:disable')" link :disabled="saving" :class="{'danger-link':row.status===1}" @click="toggle(row)">{{row.status===1?'禁用':'启用'}}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="search.page"
          v-model:page-size="search.pageSize"
          :page-sizes="[10,20,50]"
          :total="total"
          layout="total,sizes,prev,pager,next"
          @change="load"
         />
      </div>
    </PageState>
  </section>
  <el-dialog
    v-model="scoreVisible"
    title="修改积分"
    width="400px"
    :close-on-click-modal="false"
    :show-close="!saving"
    :close-on-press-escape="!saving"
  >
    <p style="margin-bottom:18px">{{selected.username}}</p>
    <el-form label-position="top">
      <el-form-item label="调整后的积分">
        <el-input-number v-model="score" :min="0" :precision="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="scoreVisible=false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!access.can('user:disable')" @click="saveScore">保存积分</el-button>
    </template>
  </el-dialog>
  <el-drawer v-model="detailVisible" title="用户资料" size="460px">
    <PageState :loading="detailLoad.loading.value" :error="detailLoad.error.value" @retry="loadDetail">
      <dl class="info-list">
        <dt>用户名</dt>
        <dd>{{detail.username}}</dd>
        <dt>用户编号</dt>
        <dd>{{detail.id}}</dd>
        <dt>手机号</dt>
        <dd>{{detail.phone||'未填写'}}</dd>
        <dt>邮箱</dt>
        <dd>{{detail.email||'未填写'}}</dd>
        <dt>积分</dt>
        <dd>{{detail.score??0}}</dd>
        <dt>注册时间</dt>
        <dd>{{detail.createTime||'未记录'}}</dd>
      </dl>
    </PageState>
  </el-drawer>
</template>
