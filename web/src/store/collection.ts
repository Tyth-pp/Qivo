import { defineStore } from 'pinia';
import { ref } from 'vue';
import { cancelCollect, collectProduct, getCollectList } from '@/api/product';
import { useUserStore } from './user';

/** 商品卡片共用收藏摘要，避免每张卡片分别查询。会话切换后不回写旧请求。 */
export const useCollectionStore = defineStore('collection', () => {
  const user = useUserStore();
  const ids = ref<number[]>([]);
  const ready = ref(false);
  const pending = ref<number[]>([]);
  let owner = '';
  let read: Promise<void> | null = null;
  let revision = 0;
  function reset() {
    owner = user.isAdmin ? '' : user.token;
    ids.value = [];
    ready.value = false;
    pending.value = [];
    read = null;
    revision++;
  }
  async function refresh() {
    const token = user.isAdmin ? '' : user.token;
    if (owner !== token) reset();
    if (!token) return;
    if (read) return read;
    const captured = revision;
    const current = getCollectList().then((response: any) => {
      if (owner === token && user.token === token && !user.isAdmin && captured === revision) {
        ids.value = (response.data || []).map(Number);
        ready.value = true;
      }
    });
    read = current;
    try { await current; }
    finally { if (read === current) read = null; }
  }
  async function toggle(id: number) {
    const token = user.token;
    if (!token || user.isAdmin) return;
    if (owner !== token) reset();
    if (pending.value.includes(id)) return;
    pending.value = [...pending.value, id];
    try {
      if (!ready.value) await refresh();
      if (user.token !== token || user.isAdmin) return;
      const saved = ids.value.includes(id);
      await (saved ? cancelCollect(id) : collectProduct(id));
      if (owner === token && user.token === token && !user.isAdmin) {
        revision++;
        ids.value = saved ? ids.value.filter(value => value !== id) : [...ids.value, id];
        return !saved;
      }
    } finally {
      if (owner === token) pending.value = pending.value.filter(value => value !== id);
    }
  }
  return { ids, ready, pending, refresh, reset, toggle };
});
