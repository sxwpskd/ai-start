<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>

      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
          type="primary"
          ghost
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>

        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署按钮
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>{{ aiLoadingText }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
          v-if="selectedElementInfo"
          class="selected-element-alert"
          type="info"
          closable
          @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                :disabled="workflowStore.generating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              :placeholder="getInputPlaceholder()"
              :rows="4"
              :maxlength="1000"
              :disabled="workflowStore.generating"
            />
            <div class="input-actions">
              <!-- 直连模式（开关=关）：codeType 选择器（无对话或构思期时出现）；
                   选非 base 类型后点发送，本次请求按该类型直接生成 -->
              <a-select
                v-if="showCodeTypeSelector"
                v-model:value="directCodeGenType"
                size="small"
                class="code-type-select"
                :options="chatCodeTypeOptions"
                :disabled="workflowStore.generating || !isOwner"
              />
              <!-- F2 生成代码按钮（工作流模式专属保险：开关=开 &&（无对话 或 构思期））——纯标记按钮：
                   点击不发任何请求，仅点亮标记；由发送按钮追加口令并分流到生成图 -->
              <!-- 旧逻辑（按钮自行发送请求，已废弃保留）：开关=开直接走工作流通道 / 开关=关弹类型选择框走直连 -->
              <!-- <a-button
                v-if="showGenCodeButton"
                size="small"
                :disabled="workflowStore.generating || !isOwner"
                @click="openCodeGenModal"
              >
                生成代码
              </a-button> -->
              <a-button
                v-if="showGenCodeButton"
                size="small"
                :type="workflowStore.genCodeMarked ? 'primary' : 'default'"
                :disabled="workflowStore.generating || !isOwner"
                @click="toggleGenCodeMark"
              >
                生成代码
              </a-button>
              <!-- F1 工作流开关 -->
              <div class="workflow-switch">
                <span class="workflow-switch-label">工作流</span>
                <a-switch
                  size="small"
                  :checked="workflowStore.workflowEnabled"
                  :disabled="workflowStore.generating"
                  @change="onWorkflowSwitchChange"
                />
              </div>
              <!-- 发送 -->
              <a-button
                type="primary"
                @click="sendMessage"
                :loading="workflowStore.generating"
                :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button
              v-if="isOwner && previewUrl"
              type="link"
              :danger="isEditMode"
              @click="toggleEditMode"
              :class="{ 'edit-mode-active': isEditMode }"
              style="padding: 0; height: auto; margin-right: 12px"
            >
              <template #icon>
                <EditOutlined />
              </template>
              {{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="!previewUrl && !workflowStore.generating" class="preview-placeholder">
            <div class="placeholder-icon">🌐</div>
            <p>网站文件生成完成后将在这里展示</p>
          </div>
          <div v-else-if="workflowStore.generating" class="preview-loading">
            <a-spin size="large" />
            <p>{{ genProgressText || '正在生成网站...' }}</p>
          </div>
          <iframe
            v-else
            :key="previewKey"
            :src="previewUrl"
            class="preview-iframe"
            frameborder="0"
            @load="onIframeLoad"
          ></iframe>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />

    <!-- F2：选择代码生成类型弹窗（旧交互，已废弃保留）：
         按钮改为纯标记后无入口——类型由后端 RouterNode 决定，不再由前端传参 -->
    <!-- <a-modal
      v-model:open="codeGenModalVisible"
      title="选择代码生成类型"
      ok-text="开始生成"
      cancel-text="取消"
      :ok-button-props="{ disabled: !selectedCodeGenType }"
      @ok="confirmCodeGen"
    >
      <a-radio-group v-model:value="selectedCodeGenType" class="code-type-radio-group">
        <a-radio v-for="opt in genTypeOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </a-radio>
      </a-radio-group>
    </a-modal> -->
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { useWorkflowStore } from '@/stores/workflow'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
// CODE_GEN_TYPE_OPTIONS 仅旧类型选择弹窗使用，按钮改纯标记后无入口（注释保留）
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const workflowStore = useWorkflowStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<any>()

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const messagesContainer = ref<HTMLElement>()

// 直连模式：codeType 选择器（默认 base=先构思再生成；选非 base 后发送即按该类型直接生成）
const directCodeGenType = ref<string>(CodeGenTypeEnum.BASE)

// 聊天页类型选项（BASE 按用户体感写作"先构思，再生成"，与首页选择器一致）
const chatCodeTypeOptions = [
  { label: '先构思，再生成', value: CodeGenTypeEnum.BASE },
  { label: '原生 HTML 模式', value: CodeGenTypeEnum.HTML },
  { label: '原生多文件模式', value: CodeGenTypeEnum.MULTI_FILE },
  { label: 'Vue 项目模式', value: CodeGenTypeEnum.VUE_PROJECT },
]

// F2 生成代码标记：纯标记（按钮不发请求）——点亮后由发送按钮在消息末尾追加口令并分流到生成图
// 旧：组件内局部状态（首页按下无法带进聊天页），改存全局 workflow store（首页/聊天页共用）
// const genCodeMarked = ref(false)

// F2 生成代码相关（旧：弹窗可见性、已选类型、三选一选项；按钮改纯标记后无入口，注释保留）
// const codeGenModalVisible = ref(false)
// const selectedCodeGenType = ref<CodeGenTypeEnum>()
// const genTypeOptions = CODE_GEN_TYPE_OPTIONS.filter(
//   (opt) => opt.value !== CodeGenTypeEnum.BASE
// )

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)

// 工作流生成进度文案（阶段 + 已耗时，来自后端 progress 事件）
const genProgressText = ref('')

/**
 * 工作流节点（后端 currentStep）→ 用户可读阶段文案
 */
const WORKFLOW_STEP_TEXT_MAP: Record<string, string> = {
  初始化: '准备中',
  图片计划: '规划素材搜集',
  内容图片收集: '搜集内容图片',
  插画图片收集: '搜集插画',
  架构图生成: '绘制架构图',
  Logo生成: '生成 Logo',
  图片聚合: '整理素材',
  提示词增强: '优化生成提示词',
  智能路由: '分析需求、选择生成方案',
  RAG检索: '检索参考资料',
  代码生成: '生成代码',
  代码质量检查: '检查代码质量',
  项目构建: '构建项目',
}

// AI 气泡等待文案：工作流生成期间同步展示后端当前运行节点（含已耗时），
// 其余场景（直连通道、构思工作流）回落为通用思考提示
const aiLoadingText = computed(() => {
  if (workflowStore.generating && genProgressText.value) {
    return genProgressText.value
  }
  return 'AI 正在思考...'
})

// 生成触发的固定文案（旧：直连 F2 与工作流通道按钮共用；按钮改纯标记后失去入口，注释保留）
// const GEN_CODE_MESSAGE = '请根据以上构思，生成代码'

// 工作流通道专属触发口令（旧：靠用户手打 + 前端嗅探识别，已废弃保留）
// const WORKFLOW_TRIGGER_COMMAND = '我已明确我的需求，现在生成代码'

// 生成口令：由「生成代码」标记按钮点亮后，发送按钮追加到用户消息末尾，前端据此改调生成端点
const GEN_CODE_COMMAND = '我已明确我的需求，现在生成代码。'

// iframe 的 :key——构思期预览地址前后不变（同为 base_{appId}/），
// 地址不变时 Vue 不会重新加载 iframe，靠改变 key 强制重建
const previewKey = ref(0)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 无对话：尚未产生任何消息（首页创建后跳转、聊天页首次进入）
const hasNoConversation = computed(() => messages.value.length === 0)

// 生成代码按钮显示条件（工作流模式专属保险）：开关=开 &&（无对话 或 构思期）
// 旧：开关=开 && 构思期；旧旧：仅按 appMode 判断（开关=关时也显示，点击会弹类型框并自行发送直连请求）
// const showGenCodeButton = computed(() => workflowStore.appMode === 'BASE')
const showGenCodeButton = computed(
  () => workflowStore.workflowEnabled && (hasNoConversation.value || workflowStore.appMode === 'BASE')
)

// 直连模式：codeType 选择器显示条件：开关=关 &&（无对话 或 构思期）
const showCodeTypeSelector = computed(
  () => !workflowStore.workflowEnabled && (hasNoConversation.value || workflowStore.appMode === 'BASE')
)

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
          }))
          .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      // 同步当前应用 codeGenType（驱动 appMode / F2 按钮显隐）
      workflowStore.setAppCodeGenType(appInfo.value.codeGenType)

      // 先加载对话历史
      await loadChatHistory()
      // 如果有至少2条对话记录，展示对应的网站
      if (messages.value.length >= 2) {
        updatePreview()
      }
      // 检查是否需要自动发送初始提示词
      // 只有在是自己的应用且没有对话历史时才自动发送
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // F1：工作流开关=开 → 工作流通道（构思期走构思图；生成期直创应用首次生成走生成图）
  if (workflowStore.workflowEnabled) {
    // 原占位逻辑（构思期/生成期均已接入真实调用）
    // message.info('工作流待转正')
    // return
    if (workflowStore.appMode === 'BASE') {
      // 首页「生成代码」保险已按下 → 首条 initPrompt 直接进生成图（标记一次性消费）
      if (workflowStore.genCodeMarked) {
        workflowStore.setGenCodeMarked(false)
        await triggerWorkflowGenerate(prompt)
        return
      }
      await sendMessageByWorkflow(prompt)
      return
    }
    // 首页显式选型直创的生成期应用：类型已锁定，RouterNode 跳过 AI 路由直接用锁定值
    await triggerWorkflowGenerate(prompt)
    return
  }

  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  workflowStore.generating = true
  await generateCode(prompt, aiMessageIndex)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || workflowStore.generating) {
    return
  }

  // 组装发送内容（含点选元素信息）——直连通道与工作流通道共用
  let sendContent = userInput.value.trim()
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    sendContent += elementContext
  }

  // F1：开关=开 + 构思期 → 工作流通道；
  // 生成代码标记点亮时，由发送按钮在消息末尾追加生成口令，并改调生成图（口令前端识别）；
  // 其余情况（开关=关，或开关=开但已是生成期）→ 直连通道（生成后改码恒走直连）
  if (workflowStore.workflowEnabled && workflowStore.appMode === 'BASE') {
    // 原占位逻辑（构思期/生成期均已接入真实调用）
    // message.info('工作流待转正')
    // return
    // 生成代码标记：纯标记按钮只负责点亮，口令在发送时注入（末尾追加，一次性消费后熄灭）
    const genCodeMarkedThisSend = workflowStore.genCodeMarked
    if (genCodeMarkedThisSend) {
      sendContent += `\n${GEN_CODE_COMMAND}`
      workflowStore.setGenCodeMarked(false)
    }
    userInput.value = ''
    // 发送消息后，清除选中元素并退出编辑模式
    if (selectedElementInfo.value) {
      clearSelectedElement()
      if (isEditMode.value) {
        toggleEditMode()
      }
    }
    // 旧：口令嗅探（靠用户手打口令触发，已废弃保留）
    // if (isWorkflowTriggerCommand(sendContent)) {
    //   await triggerWorkflowGenerate(sendContent)
    // } else {
    //   await sendMessageByWorkflow(sendContent)
    // }
    // 带口令的发送 = 生成触发（think 预检 → 生成图）；无口令 = 普通构思对话（构思图）
    if (genCodeMarkedThisSend) {
      await triggerWorkflowGenerate(sendContent)
      return
    }
    await sendMessageByWorkflow(sendContent)
    return
  }

  userInput.value = ''
  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: sendContent,
  })

  // 发送消息后，清除选中元素并退出编辑模式
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  workflowStore.generating = true
  // 直连模式：选择器选了非 base 类型 → 本次请求按该类型直接生成；
  // 工作流模式下已生成后的改码对话恒走直连（文档规则），不带类型参数
  const directGenType =
    !workflowStore.workflowEnabled && directCodeGenType.value !== CodeGenTypeEnum.BASE
      ? (directCodeGenType.value as CodeGenTypeEnum)
      : undefined
  await generateCode(sendContent, aiMessageIndex, directGenType)
}

// 生成代码 - 使用 EventSource 处理流式响应（F2 生成触发时携带 codeGenType 参数）
const generateCode = async (
  userMessage: string,
  aiMessageIndex: number,
  codeGenType?: CodeGenTypeEnum
) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: userMessage,
    })
    // F2：生成触发时携带用户选中的代码生成类型（构思期选择的目标模式）
    if (codeGenType) {
      params.set('codeGenType', codeGenType)
    }

    const url = `${baseURL}/app/chat/gen/code?${params}`

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })

    let fullContent = ''

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        // 拼接内容
        if (content !== undefined && content !== null) {
          fullContent += content
          const aiMessage = messages.value[aiMessageIndex]
          if (aiMessage) {
            aiMessage.content = fullContent
            aiMessage.loading = false
          }
          scrollToBottom()
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      workflowStore.generating = false
      eventSource?.close()

      // 延迟更新预览，确保后端已完成处理
      setTimeout(async () => {
        await fetchAppInfo()
        updatePreview()
      }, 1000)
    })

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        const aiMessage = messages.value[aiMessageIndex]
        if (aiMessage) {
          aiMessage.content = `❌ ${errorMessage}`
          aiMessage.loading = false
        }
        message.error(errorMessage)

        streamCompleted = true
        workflowStore.generating = false
        eventSource?.close()
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted || !workflowStore.generating) return
      // 检查是否是正常的连接关闭
      if (eventSource?.readyState === EventSource.CONNECTING) {
        streamCompleted = true
        workflowStore.generating = false
        eventSource?.close()

        setTimeout(async () => {
          await fetchAppInfo()
          updatePreview()
        }, 1000)
      } else {
        handleError(new Error('SSE连接错误'), aiMessageIndex)
      }
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 工作流通道（构思期 · 同步阻塞）：推送消息 → 调用构思工作流 → 回填回复 → 刷新预览
const sendMessageByWorkflow = async (content: string) => {
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  workflowStore.generating = true
  try {
    const res = await request<API.BaseResponseString>('/app/chat/think/workflow', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      data: {
        appId: appId.value,
        message: content,
      },
    })
    const aiMessage = messages.value[aiMessageIndex]
    // 业务失败：与直连通道的 business-error 处理保持一致
    if (res.data.code !== 0) {
      const errorMessage = res.data.message || '构思工作流执行失败'
      if (aiMessage) {
        aiMessage.content = `❌ ${errorMessage}`
        aiMessage.loading = false
      }
      message.error(errorMessage)
      return
    }
    if (aiMessage) {
      aiMessage.content = res.data.data || ''
      aiMessage.loading = false
    }
    scrollToBottom()
    // 后端已写入构思文件并重渲染 index.html；改变 key 强制重建 iframe 加载最新预览
    updatePreview()
    previewKey.value++
  } catch (error) {
    handleError(error, aiMessageIndex)
  } finally {
    workflowStore.generating = false
  }
}

// 判断是否为工作流生成触发口令（已废弃：随口令嗅探一并移除，保留备查）
// const isWorkflowTriggerCommand = (content: string) => content.trim() === WORKFLOW_TRIGGER_COMMAND

// 工作流通道生成触发：先做构思文档存在性预检，无构思文档时二次确认
const triggerWorkflowGenerate = async (content: string) => {
  if (workflowStore.generating) {
    return
  }
  const thinkMissing = await isThinkMissing()
  if (!thinkMissing) {
    await startWorkflowGenerate(content)
    return
  }
  Modal.confirm({
    title: '确认直接生成代码？',
    content: '当前应用还没有构思文档，将直接按初始需求生成代码，确定继续吗？',
    okText: '开始生成',
    cancelText: '再想想',
    onOk: () => startWorkflowGenerate(content),
  })
}

// 查询构思文档是否存在（预检接口异常时按"存在"处理，避免多余弹窗阻断生成）
const isThinkMissing = async (): Promise<boolean> => {
  try {
    const res = await request<API.BaseResponseBoolean>('/app/think/exists', {
      method: 'GET',
      params: { appId: appId.value },
    })
    return res.data.code === 0 && res.data.data === false
  } catch (error) {
    console.error('构思文档存在性预检失败：', error)
    return false
  }
}

// 工作流通道生成：推送用户消息与 AI 占位 → 建立 SSE 接收进度
const startWorkflowGenerate = async (content: string) => {
  // 记录生成前的模式：构思期应用生成成功后 codeGenType 才会变，用于断线对账判定
  const wasBase = workflowStore.appMode === 'BASE'

  // 添加用户消息
  messages.value.push({
    type: 'user',
    content,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  workflowStore.generating = true
  genProgressText.value = '准备中'
  generateByWorkflow(content, aiMessageIndex, wasBase)
}

// 工作流通道生成（SSE 进度推送）：progress 更新阶段文案；done 刷新预览；business-error 提示失败
const generateByWorkflow = (content: string, aiMessageIndex: number, wasBase: boolean) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false

  // 终态收尾：复位状态并关闭连接
  const finish = () => {
    streamCompleted = true
    workflowStore.generating = false
    genProgressText.value = ''
    eventSource?.close()
  }

  const baseURL = request.defaults.baseURL || API_BASE_URL
  const params = new URLSearchParams({
    appId: appId.value || '',
    message: content,
  })
  eventSource = new EventSource(`${baseURL}/app/gen/workflow?${params}`, {
    withCredentials: true,
  })

  // 进度事件：节点完成即推 + 10s 心跳（阶段文案 + 已耗时）
  eventSource.addEventListener('progress', (event: MessageEvent) => {
    if (streamCompleted) return
    try {
      const progress = JSON.parse(event.data)
      const stepText = WORKFLOW_STEP_TEXT_MAP[progress.step] || progress.step || '生成中'
      const elapsedSecond = Math.round((progress.elapsedMs || 0) / 1000)
      genProgressText.value = `${stepText}（已耗时 ${elapsedSecond} 秒）`
    } catch (error) {
      console.error('解析进度事件失败：', error)
    }
  })

  // 生成成功：后端已完成 codeGenType 落库与 AI 回复落库，刷新应用信息与预览
  eventSource.addEventListener('done', () => {
    if (streamCompleted) return
    finish()
    setTimeout(async () => {
      await fetchAppInfo()
      updatePreview()
      previewKey.value++
    }, 300)
  })

  // 业务错误（应用正在生成中 / 图内异常 / 超时等）
  eventSource.addEventListener('business-error', (event: MessageEvent) => {
    if (streamCompleted) return
    let errorMessage = '生成失败，请重试'
    try {
      const errorData = JSON.parse(event.data)
      errorMessage = errorData.message || errorMessage
    } catch (parseError) {
      console.error('解析错误事件失败：', parseError, '原始数据:', event.data)
    }
    const aiMessage = messages.value[aiMessageIndex]
    if (aiMessage) {
      aiMessage.content = `❌ ${errorMessage}`
      aiMessage.loading = false
    }
    message.error(errorMessage)
    finish()
  })

  // 连接中断：重查应用详情对账——构思期应用 codeGenType 已变即视为成功
  eventSource.onerror = async () => {
    if (streamCompleted) return
    try {
      const res = await getAppVoById({ id: appId.value as unknown as number })
      const latestType = res.data.data?.codeGenType
      if (wasBase && latestType && latestType !== CodeGenTypeEnum.BASE) {
        // 后端实际已生成成功（锁内跑完照常落库），按成功处理
        finish()
        await fetchAppInfo()
        updatePreview()
        previewKey.value++
        message.success('生成已完成')
        return
      }
    } catch (error) {
      console.error('生成结果对账失败：', error)
    }
    const aiMessage = messages.value[aiMessageIndex]
    const tipText = wasBase
      ? '连接中断，生成未完成，可重试'
      : '连接中断，生成可能仍在进行，请稍后刷新查看'
    if (aiMessage) {
      aiMessage.content = `❌ ${tipText}`
      aiMessage.loading = false
    }
    message.warning(tipText)
    finish()
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  const aiMessage = messages.value[aiMessageIndex]
  if (aiMessage) {
    aiMessage.content = '抱歉，生成过程中出现了错误，请重试。'
    aiMessage.loading = false
  }
  message.error('生成失败，请重试')
  workflowStore.generating = false
}

// F1：工作流开关切换（全局持久化；切换不清空会话）
const onWorkflowSwitchChange = (checked: boolean | string | number) => {
  workflowStore.setWorkflowEnabled(Boolean(checked))
}

// F2：生成代码标记切换——纯标记按钮，不发任何请求；点亮后由发送按钮注入口令并改调生成图
const toggleGenCodeMark = () => {
  workflowStore.setGenCodeMarked(!workflowStore.genCodeMarked)
}

// F2 旧交互（按钮自行发送请求，已废弃保留）：开关=关弹类型选择框走直连；开关=开直接走生成图
// const openCodeGenModal = () => {
//   if (workflowStore.workflowEnabled) {
//     triggerWorkflowGenerate(GEN_CODE_MESSAGE)
//     return
//   }
//   codeGenModalVisible.value = true
// }

// F2 旧：确认生成 → 复用现有 SSE 请求（仅多带 codeGenType 参数）
// const confirmCodeGen = async () => {
//   if (!selectedCodeGenType.value || workflowStore.generating) {
//     return
//   }
//   codeGenModalVisible.value = false
//   await startGenerateCode(selectedCodeGenType.value)
// }

// F2 旧：触发生成（固定文案；后端读 think 拼接增强提示词，成功后锁定 codeGenType）
// const startGenerateCode = async (codeGenType: CodeGenTypeEnum) => {
//   const fixedMessage = GEN_CODE_MESSAGE
//
//   // 添加用户消息（固定文案）
//   messages.value.push({
//     type: 'user',
//     content: fixedMessage,
//   })
//
//   // 添加AI消息占位符
//   const aiMessageIndex = messages.value.length
//   messages.value.push({
//     type: 'ai',
//     content: '',
//     loading: true,
//   })
//
//   await nextTick()
//   scrollToBottom()
//
//   // 开始生成
//   workflowStore.generating = true
//   await generateCode(fixedMessage, aiMessageIndex, codeGenType)
// }

// 更新预览
const updatePreview = () => {
  if (appId.value) {
    // const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML  // 旧：空值按 HTML 兜底
    // 历史应用 codeGenType 为空应按 BASE（构思中）处理，与 workflow store 的 appMode 判定保持一致
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.BASE
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
    previewUrl.value = newPreviewUrl
    previewReady.value = true
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    // 清理
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()

  // 监听 iframe 消息
  window.addEventListener('message', (event) => {
    visualEditor.handleIframeMessage(event)
  })
})

// 清理资源
onUnmounted(() => {
  // EventSource 会在组件卸载时自动清理
  // 重置请求状态，避免全局 generating 残留影响其他页面
  workflowStore.generating = false
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  /* 右侧预留操作区宽度（codeType 选择器/生成代码/工作流开关/发送），避免输入文字被遮挡 */
  padding-right: 320px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* F1 工作流开关 */
.workflow-switch {
  display: flex;
  align-items: center;
  gap: 4px;
}

.workflow-switch-label {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
}

/* 直连模式：codeType 选择器（与首页选择器同宽） */
.code-type-select {
  width: 150px;
}

/* F2 生成类型弹窗选项（纵向三选一） */
.code-type-radio-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}
</style>
