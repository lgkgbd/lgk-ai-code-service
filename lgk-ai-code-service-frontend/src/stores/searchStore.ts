import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSearchStore = defineStore('search', () => {
  const showHeaderSearch = ref(true)
  const headerSearchText = ref('')

  const setShowHeaderSearch = (show: boolean) => {
    showHeaderSearch.value = show
  }

  const setHeaderSearchText = (text: string) => {
    headerSearchText.value = text
  }

  return {
    showHeaderSearch,
    setShowHeaderSearch,
    headerSearchText,
    setHeaderSearchText,
  }
})