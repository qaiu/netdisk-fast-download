<template>
  <el-switch
    v-model="darkMode"
    size="large"
    style="--el-switch-on-color: #4882f8; --el-switch-off-color: #ff8000"
    inline-prompt
    :active-icon="Moon"
    :inactive-icon="Sunny"
    @change="toggleDark"
  />
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useDark, useToggle } from '@vueuse/core'
/** 引入Element-Plus图标 */
import { Sunny, Moon } from '@element-plus/icons-vue'

defineOptions({
  name: 'DarkMode'
})

// 定义事件
const emit = defineEmits(['theme-change'])

/** 切换模式 */
const isDark = useDark({})

const toggleDark = useToggle(isDark)

let item = window.localStorage.getItem("darkMode");
if (item) {
  item = (item === 'true');
}
/** 是否切换为暗黑模式 */
const darkMode = ref(item)

watch(darkMode, (newValue) => {
  console.log(`darkMode: ${newValue}`)
  window.localStorage.setItem("darkMode", newValue);
  
  // 发射主题变化事件
  emit('theme-change', newValue)
  
  // 应用主题到body
  if (newValue) {
    document.body.classList.add('dark-theme')
    document.documentElement.classList.add('dark-theme')
  } else {
    document.body.classList.remove('dark-theme')
    document.documentElement.classList.remove('dark-theme')
  }
})

onMounted(() => {
  // 初始化时发射当前主题状态
  emit('theme-change', darkMode.value)
  
  // 应用初始主题
  if (darkMode.value) {
    document.body.classList.add('dark-theme')
    document.documentElement.classList.add('dark-theme')
  }
})
</script>
