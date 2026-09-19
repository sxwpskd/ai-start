import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { CodeGenTypeEnum } from '@/utils/codeGenTypes'

const WORKFLOW_ENABLED_STORAGE_KEY = 'workflowEnabled'

/**
 * 应用模式：BASE=构思中；GENERATED=生成模式（HTML/MULTI_FILE/VUE_PROJECT）
 */
export type AppMode = 'BASE' | 'GENERATED'

/**
 * 工作流开关 / 应用模式 / 生成状态（全局状态）
 */
export const useWorkflowStore = defineStore('workflow', () => {
  // 工作流开关：全局用户级，默认关闭，localStorage 持久化；切换不清空会话
  const workflowEnabled = ref<boolean>(
    localStorage.getItem(WORKFLOW_ENABLED_STORAGE_KEY) === 'true'
  )

  // 更新工作流开关（同步持久化）
  function setWorkflowEnabled(value: boolean) {
    workflowEnabled.value = value
    localStorage.setItem(WORKFLOW_ENABLED_STORAGE_KEY, String(value))
  }

  // 「生成代码」保险标记（工作流模式专属）：点亮表示本次触发走生成图，不发任何请求；
  // 存全局是为了首页按下后能带进聊天页（首条 initPrompt 直接进生成图），用完即熄灭
  const genCodeMarked = ref(false)

  function setGenCodeMarked(value: boolean) {
    genCodeMarked.value = value
  }

  // 当前应用 codeGenType（由聊天页同步）
  const appCodeGenType = ref<string>()

  // appMode：由 App.codeGenType 派生——空/BASE=构思中；其余=生成模式
  const appMode = computed<AppMode>(() => {
    const codeGenType = appCodeGenType.value
    return !codeGenType || codeGenType === CodeGenTypeEnum.BASE ? 'BASE' : 'GENERATED'
  })

  // 同步当前应用 codeGenType（驱动 appMode 更新）
  function setAppCodeGenType(codeGenType?: string) {
    appCodeGenType.value = codeGenType
  }

  // 请求进行中，期间禁用开关与按钮
  const generating = ref(false)

  return {
    workflowEnabled,
    setWorkflowEnabled,
    genCodeMarked,
    setGenCodeMarked,
    appCodeGenType,
    appMode,
    setAppCodeGenType,
    generating,
  }
})
