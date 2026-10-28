import { ref } from 'vue';
/** 筛选切换后，只允许最后一次读取更新界面。 */
export function useLoad() {
    const loading = ref(false), error = ref<boolean | 'forbidden'>(false);
    let sequence = 0;
    async function run<T>(read: () => Promise<T>, apply: (value: T) => void) {
        const current = ++sequence;
        loading.value = true;
        error.value = false;
        try {
            const value = await read();
            if (current === sequence)
                apply(value);
        }
        catch (failure) {
            if (current === sequence)
                error.value = (failure as { response?: { status?: number } })?.response?.status === 403 ? 'forbidden' : true;
        }
        finally {
            if (current === sequence)
                loading.value = false;
        }
    }
    return { loading, error, run };
}
