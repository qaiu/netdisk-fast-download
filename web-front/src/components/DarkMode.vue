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
import { ref,watch } from 'vue'
import { useDark, useToggle } from '@vueuse/core'
/** 引入Element-Plus图标 */
import { Sunny, Moon } from '@element-plus/icons-vue'
defineOptions({
  name: 'DarkMode'
})

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
})
</script>
