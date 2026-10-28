import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCartStore = defineStore('cart', () => {
  const count = ref(0)

  function setCount(n: number) {
    count.value = n
  }

  function addCount(n: number) {
    count.value += n
  }

  return { count, setCount, addCount }
})
