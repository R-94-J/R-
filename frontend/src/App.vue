<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { api, refreshCsrf } from './api'
import Icon from './components/Icon.vue'
import Modal from './components/Modal.vue'
import SourceInfo from './components/SourceInfo.vue'
import DocumentSourceFields from './components/DocumentSourceFields.vue'
import ConversationActions from './components/ConversationActions.vue'
import RetrievalPanel from './components/RetrievalPanel.vue'

const page = ref('chat')
const adminPortal = window.location.pathname.startsWith('/admin')
const adminMode = computed(() => adminPortal && auth.value.admin)
const visiblePages = computed(() => (adminMode.value ? pageTitles : { chat: '知识问答' }))
const mobileNav = ref(false)
const ready = ref(false)
const bootError = ref('')
const status = ref({
  documentCount: 0,
  chunkCount: 0,
  answerCount: 0,
  helpfulCount: 0,
  aiEnabled: false,
  indexedChunkCount: 0,
})
const auth = ref({ admin: false, username: '访客' })
const documents = ref([])
const conversations = ref([])
const historySearch = ref('')
const filteredConversations = computed(() =>
  conversations.value.filter((conversation) =>
    conversation.title.toLowerCase().includes(historySearch.value.trim().toLowerCase()),
  ),
)
const activeConversation = computed(() =>
  conversations.value.find((conversation) => conversation.id === activeId.value),
)
const messages = ref([])
const activeId = ref(null)
const question = ref('')
const sending = ref(false)
const pendingQuestion = ref('')
const historyLoading = ref(false)
const thread = ref(null)
const questionInput = ref(null)
const search = ref('')
const category = ref('全部资料')
const loginOpen = ref(false)
const loginBusy = ref(false)
const loginError = ref('')
const username = ref('admin')
const password = ref('')
const registering = ref(false)
const confirmPassword = ref('')
const uploadSource = ref({
  sourceUrl: '',
  publisher: '',
  publishedDate: '',
  applicability: '',
  downloadUrl: '',
})
const uploadOpen = ref(false)
const uploadBusy = ref(false)
const uploadError = ref('')
const uploadTitle = ref('')
const uploadCategory = ref('教务办事')
const uploadFile = ref(null)
const editOpen = ref(false)
const editBusy = ref(false)
const editError = ref('')
const editDoc = ref(null)
const editForm = ref({
  title: '',
  category: '其他资料',
  sourceUrl: '',
  publisher: '',
  publishedDate: '',
  applicability: '',
  downloadUrl: '',
})
const fileInput = ref(null)
const dragging = ref(false)
const previewOpen = ref(false)
const preview = ref(null)
const previewLoading = ref(false)
const previewError = ref('')
const indexBusy = ref('')
const toast = ref(null)
let toastTimer
let historyRequest = 0
let previewRequest = 0
const categories = computed(() => [
  '全部资料',
  ...new Set(documents.value.map((doc) => doc.category)),
])
const standardCategories = [
  '学籍与学位',
  '教务办事',
  '办事表格',
  '实习与毕设',
  '奖助学金',
  '学生管理',
  '校园生活',
  '学习资源',
  '规章制度',
  '其他资料',
]
const categoryOptions = computed(() => [
  ...new Set([...standardCategories, ...documents.value.map((doc) => doc.category)]),
])
const filteredDocuments = computed(() =>
  documents.value.filter(
    (doc) =>
      (category.value === '全部资料' || doc.category === category.value) &&
      `${doc.title} ${doc.filename} ${doc.category}`
        .toLowerCase()
        .includes(search.value.toLowerCase()),
  ),
)
const pageTitles = {
  chat: '知识问答',
  documents: '知识库',
  retrieval: '检索调试',
  overview: '系统概览',
}
const pageIcons = { chat: 'chat', documents: 'book', retrieval: 'search', overview: 'chart' }
const modeLabels = {
  local: '知识库原文',
  model: '知识库回答',
  fallback: '原文回退',
  insufficient: '资料不足',
  website: '官网检索回答',
  website_extract: '官网原文',
  general: '非官方参考',
  out_of_scope: '超出服务范围',
  unavailable: '服务暂不可用',
}
const suggestions = [
  {
    icon: 'cap',
    title: '学士学位需要多少绩点？',
    question: '山东青年政治学院2025年学士学位授予管理办法规定，平均学分绩点要达到多少？',
    tag: '学籍与学位',
  },
  {
    icon: 'grid',
    title: '2025级如何申请转专业？',
    question: '2025级学生申请转专业需要修满几学期？什么时候报名？',
    tag: '学籍与学位',
  },
  {
    icon: 'book',
    title: '毕设选题有什么要求？',
    question: '本科毕业论文（设计）工作管理办法对选题有哪些要求？请说明文件版本。',
    tag: '实习与毕设',
  },
  {
    icon: 'clock',
    title: '共享空间预约如何签到？',
    question: '图书馆信息共享空间预约后，应该在多少分钟内刷卡登记？',
    tag: '学习资源',
  },
]

function notify(message, type = 'success') {
  clearTimeout(toastTimer)
  toast.value = { message, type }
  toastTimer = setTimeout(() => {
    toast.value = null
  }, 5000)
}
function showError(error) {
  if (error.status === 401 || error.status === 403) {
    auth.value = { admin: false, username: '学生' }
    resetHistory()
    previewRequest++
    documents.value = []
    preview.value = null
    previewOpen.value = false
    uploadOpen.value = false
    editOpen.value = false
    editDoc.value = null
    page.value = 'chat'
    if (adminPortal) loginOpen.value = true
  }
  notify(error.message, 'error')
}
function resetHistory() {
  historyRequest++
  conversations.value = []
  historySearch.value = ''
  activeId.value = null
  messages.value = []
}
function conversationRenamed(updated) {
  const current = conversations.value.find((item) => item.id === updated.id)
  if (current) Object.assign(current, updated)
}
async function refreshData() {
  const [s, d, c] = await Promise.all([
    api(adminMode.value ? '/status' : '/chat/config'),
    adminMode.value ? api('/documents') : Promise.resolve([]),
    api('/conversations'),
  ])
  status.value = s
  documents.value = d
  conversations.value = c
  if (!categories.value.includes(category.value)) category.value = '全部资料'
}
async function bootstrap() {
  bootError.value = ''
  try {
    await refreshCsrf()
    auth.value = await api('/auth/me')
    await refreshData()
    ready.value = true
    if (adminPortal) {
      if (auth.value.admin) page.value = 'documents'
      else loginOpen.value = true
    }
  } catch (error) {
    bootError.value = error.message
  }
}
onMounted(bootstrap)
function navigate(target) {
  if (target !== 'chat' && !adminMode.value) return
  page.value = target
  mobileNav.value = false
}
function selectCategory(item) {
  category.value = item
  navigate('documents')
}
function openStudentLogin() {
  if (auth.value.authenticated) return logout()
  username.value = ''
  registering.value = false
  loginError.value = ''
  loginOpen.value = true
}
function toggleRegistration() {
  registering.value = !registering.value
  loginError.value = ''
  confirmPassword.value = ''
}
function closePreview() {
  previewOpen.value = false
  previewRequest++
}
async function scrollBottom() {
  await nextTick()
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight
}
function newChat() {
  if (sending.value || historyLoading.value) return
  historyRequest++
  activeId.value = null
  messages.value = []
  question.value = ''
  navigate('chat')
  nextTick(() => questionInput.value?.focus())
}
async function openConversation(id) {
  if (sending.value || historyLoading.value) return
  const request = ++historyRequest
  historyLoading.value = true
  navigate('chat')
  try {
    const data = await api(`/conversations/${id}/messages`)
    if (request !== historyRequest) return
    activeId.value = id
    messages.value = data
    await scrollBottom()
  } catch (error) {
    showError(error)
  } finally {
    historyLoading.value = false
  }
}
async function removeConversation(conversation) {
  if (!window.confirm(`删除对话“${conversation.title}”？`)) return
  try {
    await api(`/conversations/${conversation.id}`, { method: 'DELETE' })
    if (activeId.value === conversation.id) newChat()
    await refreshData()
    notify('对话已删除')
  } catch (error) {
    showError(error)
  }
}
async function send(text = question.value) {
  const content = text.trim()
  if (!content || sending.value || historyLoading.value || !ready.value) return
  if (content.length > 600) return notify('问题最多 600 字', 'error')
  navigate('chat')
  sending.value = true
  pendingQuestion.value = content
  question.value = ''
  await scrollBottom()
  try {
    const result = await api('/chat', {
      method: 'POST',
      body: { question: content, conversationId: activeId.value },
    })
    activeId.value = result.conversationId
    messages.value.push(result.userMessage, result.assistantMessage)
    pendingQuestion.value = ''
    if (result.retrievalDegraded) notify('本次部分检索服务不可用，请留意回答中的说明', 'info')
    await refreshData().catch(showError)
  } catch (error) {
    question.value = content
    showError(error)
  } finally {
    sending.value = false
    pendingQuestion.value = ''
    await scrollBottom()
  }
}
function onQuestionKey(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    send()
  }
}
async function feedback(message, value) {
  try {
    const result = await api(`/messages/${message.id}/feedback`, {
      method: 'POST',
      body: { value: message.feedback === value ? null : value },
    })
    Object.assign(message, result)
    if (adminMode.value) status.value = await api('/status')
  } catch (error) {
    showError(error)
  }
}
async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    notify('回答已复制')
  } catch {
    notify('复制失败，请手动选择文字复制', 'error')
  }
}
async function login() {
  loginBusy.value = true
  loginError.value = ''
  try {
    if (registering.value) {
      if (password.value !== confirmPassword.value) throw new Error('两次输入的密码不一致')
      await api('/auth/register', {
        method: 'POST',
        body: { username: username.value, password: password.value },
      })
      registering.value = false
      confirmPassword.value = ''
    }
    await api('/auth/login', {
      method: 'POST',
      body: new URLSearchParams({ username: username.value, password: password.value }),
    })
    await refreshCsrf()
    auth.value = await api('/auth/me')
    if (adminPortal && !auth.value.admin) {
      await api('/auth/logout', { method: 'POST' })
      await refreshCsrf()
      auth.value = { admin: false, authenticated: false, username: '学生访客' }
      throw new Error('该账号是学生账号，请前往学生端登录')
    }
    password.value = ''
    loginOpen.value = false
    resetHistory()
    question.value = ''
    notify(auth.value.admin ? '管理员登录成功' : '学生登录成功，对话将保存到你的账号')
    await refreshData()
    if (adminPortal) navigate('documents')
  } catch (error) {
    loginError.value = error.message
  } finally {
    loginBusy.value = false
  }
}
async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
    await refreshCsrf()
    auth.value = { admin: false, username: '学生' }
    resetHistory()
    previewRequest++
    documents.value = []
    preview.value = null
    previewOpen.value = false
    uploadOpen.value = false
    editOpen.value = false
    editDoc.value = null
    newChat()
    await refreshData()
    notify('已退出账号')
  } catch (error) {
    showError(error)
  }
}
function openUpload() {
  if (!auth.value.admin) {
    loginOpen.value = true
    return
  }
  uploadFile.value = null
  uploadTitle.value = ''
  uploadCategory.value = '教务办事'
  uploadError.value = ''
  uploadOpen.value = true
  uploadSource.value = {
    sourceUrl: '',
    publisher: '',
    publishedDate: '',
    applicability: '',
    downloadUrl: '',
  }
  if (fileInput.value) fileInput.value.value = ''
}
function chooseFile(file) {
  dragging.value = false
  if (!file) return
  if (!/\.(pdf|docx|txt|md)$/i.test(file.name)) {
    uploadError.value = '仅支持 PDF、DOCX、TXT、MD 文件'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    uploadError.value = '文件不能超过 10 MB'
    return
  }
  uploadError.value = ''
  uploadFile.value = file
  if (!uploadTitle.value) uploadTitle.value = file.name.replace(/\.[^.]+$/, '').slice(0, 120)
}
async function upload() {
  if (!uploadFile.value || uploadBusy.value) return
  uploadBusy.value = true
  uploadError.value = ''
  const form = new FormData()
  form.append('file', uploadFile.value)
  form.append('title', uploadTitle.value)
  form.append('category', uploadCategory.value)
  for (const [key, value] of Object.entries(uploadSource.value)) if (value) form.append(key, value)
  try {
    await api('/documents', { method: 'POST', body: form })
    uploadOpen.value = false
    await refreshData()
    notify('资料上传成功，已完成解析和索引')
  } catch (error) {
    uploadError.value = error.message
    if (error.status === 401 || error.status === 403) showError(error)
  } finally {
    uploadBusy.value = false
  }
}
async function removeDocument(doc) {
  if (!window.confirm(`删除“${doc.title}”及其检索索引？历史回答中的引用快照仍会保留。`)) return
  try {
    await api(`/documents/${doc.id}`, { method: 'DELETE' })
    await refreshData()
    notify('文档已删除')
  } catch (error) {
    showError(error)
  }
}
function openEdit(doc) {
  if (!auth.value.admin) {
    loginOpen.value = true
    return
  }
  previewOpen.value = false
  previewRequest++
  editDoc.value = doc
  editError.value = ''
  editOpen.value = true
  editForm.value = {
    title: doc.title,
    category: doc.category,
    sourceUrl: doc.sourceUrl || '',
    publisher: doc.publisher || '',
    publishedDate: doc.publishedDate || '',
    applicability: doc.applicability || '',
    // A stored original keeps its own /api/files link; only external links are editable here.
    downloadUrl: /^https?:\/\//i.test(doc.downloadUrl || '') ? doc.downloadUrl : '',
  }
}
async function saveEdit() {
  if (!editDoc.value || editBusy.value) return
  editBusy.value = true
  editError.value = ''
  try {
    const updated = await api(`/documents/${editDoc.value.id}`, {
      method: 'PATCH',
      body: { ...editForm.value, publishedDate: editForm.value.publishedDate || null },
    })
    editOpen.value = false
    if (preview.value?.document.id === updated.id) preview.value.document = updated
    await refreshData()
    notify('资料信息已更新，新回答将使用更新后的来源')
  } catch (error) {
    editError.value = error.message
    if (error.status === 401 || error.status === 403) showError(error)
  } finally {
    editBusy.value = false
  }
}
async function reindex(doc) {
  if (indexBusy.value) return
  indexBusy.value = doc.id
  try {
    await api(`/documents/${doc.id}/reindex`, { method: 'POST' })
    await refreshData()
    notify('向量索引已更新')
  } catch (error) {
    showError(error)
  } finally {
    indexBusy.value = ''
  }
}
async function openDocument(id, chunkId) {
  if (!adminMode.value) return
  const request = ++previewRequest
  previewOpen.value = true
  previewLoading.value = true
  preview.value = null
  previewError.value = ''
  try {
    const result = await api(`/documents/${id}`)
    if (request !== previewRequest) return
    preview.value = result
    previewLoading.value = false
    await nextTick()
    if (chunkId) document.getElementById(`chunk-${chunkId}`)?.scrollIntoView({ block: 'center' })
  } catch (error) {
    if (request === previewRequest) previewError.value = error.message
  } finally {
    if (request === previewRequest) previewLoading.value = false
  }
}
function date(value) {
  return new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
function size(value) {
  return value < 1024
    ? `${value} B`
    : value < 1024 * 1024
      ? `${(value / 1024).toFixed(1)} KB`
      : `${(value / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="app-shell">
    <button
      v-if="mobileNav"
      class="sidebar-overlay"
      aria-label="关闭导航"
      @click="mobileNav = false"
    ></button>
    <aside :class="['sidebar', { open: mobileNav }]">
      <a href="#" class="brand" @click.prevent="newChat">
        <img class="brand-mark" src="/campus-icon.png" alt="" width="43" height="43" />
        <span>
          知校园
          <small>CAMPUS KNOWLEDGE</small>
        </span>
      </a>
      <button class="new-chat" :disabled="sending || historyLoading" @click="newChat">
        <Icon name="plus" :size="18" />
        开启新对话
        <span class="shortcut">＋</span>
      </button>
      <div class="nav-label">{{ adminPortal ? '管理员端 · 知识管理' : '学生端 · 校园问答' }}</div>
      <nav aria-label="主导航">
        <button
          v-for="(title, key) in visiblePages"
          :key="key"
          :class="['nav-item', { active: page === key }]"
          @click="navigate(key)"
        >
          <Icon :name="pageIcons[key]" />
          <span>{{ title }}</span>
          <span v-if="key === 'documents'" class="nav-count">{{ status.documentCount }}</span>
        </button>
      </nav>
      <div class="history-heading">
        <span class="nav-label">最近对话</span>
        <span>
          {{
            historySearch.trim()
              ? `${filteredConversations.length} / ${conversations.length}`
              : conversations.length
          }}
        </span>
      </div>
      <div class="history-search">
        <Icon name="search" :size="15" />
        <input
          v-model="historySearch"
          aria-label="搜索对话标题"
          placeholder="搜索对话标题"
          maxlength="80"
        />
        <button
          v-if="historySearch"
          class="icon-button"
          aria-label="清空对话搜索"
          @click="historySearch = ''"
        >
          <Icon name="close" :size="14" />
        </button>
      </div>
      <div class="conversation-list">
        <div v-if="!conversations.length" class="history-empty">
          每一个问题，都是新发现。
          <br />
          你的对话会显示在这里。
        </div>
        <div v-else-if="!filteredConversations.length" class="history-empty">
          没有匹配的对话标题，请换一个关键词。
        </div>
        <div
          v-for="conversation in filteredConversations"
          :key="conversation.id"
          :class="[
            'conversation-item',
            { selected: activeId === conversation.id && page === 'chat' },
          ]"
        >
          <button
            class="conversation-link"
            :title="conversation.title"
            :disabled="sending || historyLoading"
            @click="openConversation(conversation.id)"
          >
            <Icon name="chat" :size="15" />
            <span>{{ conversation.title }}</span>
          </button>
          <button
            class="history-delete icon-button"
            :disabled="sending || historyLoading"
            :aria-label="`删除对话 ${conversation.title}`"
            @click="removeConversation(conversation)"
          >
            <Icon name="trash" :size="14" />
          </button>
        </div>
      </div>
      <div class="sidebar-bottom">
        <div class="workspace-note">
          <Icon name="shield" :size="17" />
          <div>
            让每个回答有据可查
            <small>基于校园资料 · 保留来源引用</small>
          </div>
        </div>
        <a class="portal-link" :href="adminPortal ? '/student' : '/admin'">
          {{ adminPortal ? '进入学生端 →' : '管理员入口 →' }}
        </a>
        <button
          v-if="adminPortal"
          class="profile"
          :disabled="sending || historyLoading"
          @click="auth.admin ? logout() : (loginOpen = true)"
        >
          <span class="avatar"><Icon name="shield" :size="19" /></span>
          <span>
            {{ auth.admin ? auth.username : '管理员登录' }}
            <small>{{ auth.admin ? '管理员端 · 点击退出' : '登录后可管理和上传知识文件' }}</small>
          </span>
          <Icon :name="auth.admin ? 'logout' : 'arrow'" :size="16" />
        </button>
        <button
          v-else
          class="profile"
          :disabled="sending || historyLoading"
          @click="openStudentLogin"
        >
          <span class="avatar"><Icon name="user" :size="19" /></span>
          <span>
            {{ auth.authenticated ? auth.username : '学生登录 / 注册' }}
            <small>
              {{ auth.authenticated ? '对话保存到账号 · 点击退出' : '注册账号，随时找回对话记录' }}
            </small>
          </span>
          <Icon :name="auth.authenticated ? 'logout' : 'arrow'" :size="16" />
        </button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="breadcrumb">
          <button class="icon-button mobile-menu" aria-label="打开导航" @click="mobileNav = true">
            <Icon name="menu" />
          </button>
          <span>{{ adminPortal ? '管理员端' : '学生端' }}</span>
          <span class="slash">/</span>
          <strong>{{ adminPortal && !auth.admin ? '身份验证' : pageTitles[page] }}</strong>
        </div>
        <div class="topbar-right">
          <span class="status-pill">
            <i :class="{ offline: !ready }"></i>
            {{ ready ? (status.aiEnabled ? 'AI 问答已配置' : '本地检索模式') : '连接服务中' }}
          </span>
          <span class="version">v1.1</span>
        </div>
      </header>
      <div v-if="bootError" class="boot-error" role="alert">
        <Icon name="info" />
        {{ bootError }}
        <button class="text-button" @click="bootstrap">重新连接</button>
      </div>

      <section v-if="adminPortal && !auth.admin" class="admin-gate">
        <span class="welcome-emblem"><Icon name="shield" :size="32" /></span>
        <h1>校园知识库 · 管理员端</h1>
        <p>登录后可在知识库中直接上传 PDF、Word 和文本资料，查看解析结果并维护知识来源。</p>
        <button class="primary-button" :disabled="!ready" @click="loginOpen = true">
          管理员登录
        </button>
        <a href="/student" class="text-button">我是学生，进入问答 →</a>
      </section>
      <section v-else-if="page === 'chat'" class="chat-layout">
        <div class="chat-main">
          <div v-if="activeConversation" class="conversation-bar">
            <h2 :title="activeConversation.title">{{ activeConversation.title }}</h2>
            <ConversationActions
              :key="activeId"
              :conversation="activeConversation"
              :disabled="sending || historyLoading"
              @renamed="conversationRenamed"
              @notify="notify"
              @error="showError"
            />
          </div>
          <div
            ref="thread"
            class="thread"
            aria-live="polite"
            :aria-busy="sending || historyLoading"
          >
            <div v-if="historyLoading" class="loading-state">
              <span class="spinner"></span>
              正在加载对话…
            </div>
            <div v-else-if="!messages.length && !sending" class="welcome">
              <div class="eyebrow">
                <span></span>
                山东青年政治学院 · 校园知识助手
              </div>
              <div class="welcome-emblem"><Icon name="spark" :size="33" /></div>
              <h1>
                校园里的问题，
                <br />
                <span>在这里找到答案。</span>
              </h1>
              <p class="welcome-description">
                只回答学校事务与学习、学术研究相关问题。
                <br />
                知识库优先，官网补充；非官方参考会明确标注。
              </p>
              <div class="suggestions-heading">
                <span>试着问我</span>
                <span>
                  从一个小问题开始
                  <Icon name="arrow" :size="14" />
                </span>
              </div>
              <div class="suggestions">
                <button
                  v-for="item in suggestions"
                  :key="item.title"
                  class="suggestion-card"
                  :disabled="!ready"
                  @click="send(item.question)"
                >
                  <span class="suggestion-icon"><Icon :name="item.icon" /></span>
                  <span class="suggestion-tag">{{ item.tag }}</span>
                  <strong>{{ item.title }}</strong>
                  <Icon name="arrow" class="suggestion-arrow" :size="17" />
                </button>
              </div>
              <div class="demo-note">
                <Icon name="info" :size="14" />
                依据学校官网公开资料回答，请关注文件版本、适用年级及最新通知。
              </div>
            </div>
            <div v-else class="message-list">
              <div class="conversation-heading">
                <span class="eyebrow">校园知识问答</span>
                <span>答案可追溯，信息有来源</span>
              </div>
              <article
                v-for="message in messages"
                :key="message.id"
                :class="['message', message.role]"
              >
                <span class="message-avatar">
                  <Icon :name="message.role === 'user' ? 'user' : 'spark'" :size="20" />
                </span>
                <div class="message-body">
                  <div class="message-heading">
                    <strong>{{ message.role === 'user' ? '你' : '知校园助手' }}</strong>
                    <span v-if="message.mode" class="mode-label" :class="message.mode">
                      {{ modeLabels[message.mode] }}
                    </span>
                  </div>
                  <div class="message-content">{{ message.content }}</div>
                  <div v-if="message.sources.length" class="sources">
                    <div class="sources-title">
                      <Icon name="shield" :size="14" />
                      参考来源 · {{ message.sources.length }} 个片段
                    </div>
                    <details
                      v-for="source in message.sources"
                      :key="source.chunkId"
                      class="source-card"
                    >
                      <summary>
                        <span class="source-number">{{ source.index }}</span>
                        <span>{{ source.title }}</span>
                        <span class="source-position">片段 {{ source.position }}</span>
                      </summary>
                      <p>{{ source.excerpt }}</p>
                      <button
                        v-if="adminMode && source.documentId"
                        class="text-button"
                        @click="openDocument(source.documentId, source.chunkId)"
                      >
                        查看完整资料
                        <Icon name="arrow" :size="14" />
                      </button>
                    </details>
                  </div>
                  <SourceInfo
                    v-for="source in message.sources.filter(
                      (item, index, all) =>
                        all.findIndex(
                          (other) =>
                            (other.sourceUrl || other.documentId) ===
                            (item.sourceUrl || item.documentId),
                        ) === index,
                    )"
                    :key="source.sourceUrl || source.documentId"
                    :source="source"
                  />
                  <div v-if="message.role === 'assistant'" class="message-actions">
                    <span>{{ (message.latencyMs / 1000).toFixed(2) }} 秒</span>
                    <button
                      class="icon-button"
                      aria-label="复制回答"
                      @click="copy(message.content)"
                    >
                      <Icon name="copy" :size="15" />
                    </button>
                    <button
                      :class="['icon-button', { chosen: message.feedback === 1 }]"
                      aria-label="回答有帮助"
                      :aria-pressed="message.feedback === 1"
                      @click="feedback(message, 1)"
                    >
                      <Icon name="up" :size="15" />
                    </button>
                    <button
                      :class="['icon-button', { chosen: message.feedback === -1 }]"
                      aria-label="回答需改进"
                      :aria-pressed="message.feedback === -1"
                      @click="feedback(message, -1)"
                    >
                      <Icon name="down" :size="15" />
                    </button>
                  </div>
                </div>
              </article>
              <template v-if="sending && pendingQuestion">
                <article class="message user">
                  <span class="message-avatar"><Icon name="user" :size="20" /></span>
                  <div class="message-body">
                    <div class="message-heading"><strong>你</strong></div>
                    <div class="message-content">{{ pendingQuestion }}</div>
                  </div>
                </article>
                <article class="message assistant">
                  <span class="message-avatar"><Icon name="spark" :size="20" /></span>
                  <div class="thinking">
                    <span class="spinner"></span>
                    {{ status.aiEnabled ? '正在检索资料并组织回答…' : '正在校园知识库中查找依据…' }}
                  </div>
                </article>
              </template>
            </div>
          </div>
          <div class="composer-area">
            <form class="composer" @submit.prevent="send()">
              <label class="sr-only" for="question">输入校园问题</label>
              <textarea
                id="question"
                ref="questionInput"
                v-model="question"
                rows="2"
                maxlength="600"
                placeholder="关于校园，你想了解什么？"
                :disabled="!ready || sending || historyLoading"
                @keydown="onQuestionKey"
              ></textarea>
              <div class="composer-toolbar">
                <span>
                  <Icon name="book" :size="15" />
                  校园知识库
                  <span class="composer-count">{{ status.documentCount }} 份资料</span>
                </span>
                <div>
                  <span class="character-count">{{ question.length }}/600</span>
                  <button
                    class="send-button"
                    aria-label="发送问题"
                    :disabled="!ready || !question.trim() || sending || historyLoading"
                  >
                    <Icon name="send" :size="18" />
                  </button>
                </div>
              </div>
            </form>
            <div class="composer-footnote">
              <span>
                {{
                  status.aiEnabled
                    ? '回答基于检索资料生成，请结合来源核对。'
                    : '当前展示相关原文；配置模型接口后可启用 AI 回答。'
                }}
              </span>
              <span>Enter 发送 · Shift + Enter 换行</span>
            </div>
          </div>
        </div>

        <aside v-if="adminMode" class="context-panel">
          <div class="panel-heading">
            <h2>知识库管理</h2>
            <span class="live-dot"></span>
          </div>
          <div class="mini-stats">
            <div>
              <strong>{{ status.documentCount }}</strong>
              <span>知识文档</span>
            </div>
            <div>
              <strong>{{ status.chunkCount }}</strong>
              <span>知识片段</span>
            </div>
          </div>
          <button class="primary-button full-width" @click="openUpload">
            <Icon name="upload" :size="17" />
            上传资料
          </button>
          <div class="panel-section-heading"><h3>知识分类</h3></div>
          <div class="category-links">
            <button v-for="item in categories.slice(1)" :key="item" @click="selectCategory(item)">
              <span>{{ item }}</span>
              <Icon name="arrow" :size="13" />
            </button>
          </div>
          <button
            v-for="doc in documents.slice(0, 3)"
            :key="doc.id"
            class="recent-document"
            @click="openDocument(doc.id)"
          >
            <span class="file-icon"><Icon name="file" :size="18" /></span>
            <span>
              <strong>{{ doc.title }}</strong>
              <small>{{ doc.fileType.toUpperCase() }} · {{ date(doc.createdAt) }} 更新</small>
            </span>
          </button>
        </aside>
      </section>

      <RetrievalPanel
        v-else-if="adminMode && page === 'retrieval'"
        :embedding-enabled="status.embeddingEnabled"
        @preview="openDocument"
        @error="showError"
      />

      <section v-else-if="adminMode && page === 'documents'" class="content-page">
        <div class="page-heading">
          <div>
            <span class="eyebrow">KNOWLEDGE LIBRARY</span>
            <h1>让校园知识，有处可寻。</h1>
            <p>管理校园指南、规章制度和办事资料，让每次回答都有依据。</p>
          </div>
          <button class="primary-button" :disabled="!ready" @click="openUpload">
            <Icon name="upload" :size="17" />
            {{ auth.admin ? '上传资料' : '登录后上传' }}
          </button>
        </div>
        <div class="library-summary">
          <div>
            <span class="summary-icon"><Icon name="book" :size="23" /></span>
            <div>
              <strong>
                {{ status.documentCount }}
                <small>份资料</small>
              </strong>
              <p>校园知识持续积累</p>
            </div>
          </div>
          <div>
            <span class="summary-icon amber"><Icon name="grid" :size="23" /></span>
            <div>
              <strong>
                {{ status.chunkCount }}
                <small>个片段</small>
              </strong>
              <p>已解析，可用于检索</p>
            </div>
          </div>
          <div>
            <span class="summary-icon blue"><Icon name="shield" :size="23" /></span>
            <div>
              <strong>
                {{ categories.length - 1 }}
                <small>个分类</small>
              </strong>
              <p>覆盖学习与校园生活</p>
            </div>
          </div>
        </div>
        <div class="library-toolbar">
          <div class="category-tabs" role="group" aria-label="资料分类">
            <button
              v-for="item in categories"
              :key="item"
              :class="{ active: category === item }"
              @click="category = item"
            >
              {{ item }}
            </button>
          </div>
          <label class="search-box">
            <Icon name="search" :size="17" />
            <input v-model="search" placeholder="搜索文档名称或分类" aria-label="搜索文档" />
          </label>
        </div>
        <div class="document-table-wrap">
          <table class="document-table">
            <thead>
              <tr>
                <th>文档名称</th>
                <th>分类</th>
                <th>知识片段</th>
                <th>加入时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="doc in filteredDocuments" :key="doc.id">
                <td>
                  <button class="document-title" @click="openDocument(doc.id)">
                    <span class="document-type" :class="doc.fileType">
                      {{ doc.fileType.toUpperCase() }}
                    </span>
                    <span>
                      <strong>{{ doc.title }}</strong>
                      <small>
                        {{ size(doc.fileSize) }} · {{ doc.characterCount.toLocaleString() }} 字
                        <span v-if="doc.demo" class="demo-badge">演示</span>
                        <span v-if="!doc.sourceUrl" class="missing-badge">待补来源</span>
                      </small>
                    </span>
                  </button>
                </td>
                <td>
                  <span class="category-badge">{{ doc.category }}</span>
                </td>
                <td>
                  <span class="indexed-label">
                    <Icon name="check" :size="14" />
                    {{ doc.chunkCount }} 个片段
                  </span>
                </td>
                <td class="date-cell">{{ date(doc.createdAt) }}</td>
                <td>
                  <div class="table-actions">
                    <button class="text-button" @click="openDocument(doc.id)">查看</button>
                    <button v-if="auth.admin" class="text-button" @click="openEdit(doc)">
                      编辑
                    </button>
                    <button
                      v-if="auth.admin && status.embeddingEnabled"
                      class="icon-button"
                      :disabled="!!indexBusy"
                      :aria-label="`更新向量索引 ${doc.title}`"
                      @click="reindex(doc)"
                    >
                      <Icon name="refresh" :class="{ spinning: indexBusy === doc.id }" :size="17" />
                    </button>
                    <button
                      v-if="auth.admin"
                      class="icon-button danger"
                      :aria-label="`删除文档 ${doc.title}`"
                      @click="removeDocument(doc)"
                    >
                      <Icon name="trash" :size="17" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!filteredDocuments.length" class="empty-state">
            <Icon name="search" :size="35" />
            <h3>{{ documents.length ? '没有找到相关资料' : '知识库还是空的' }}</h3>
            <p>
              {{
                documents.length ? '试试其他关键词或分类。' : '上传校园文档，开启第一次知识问答。'
              }}
            </p>
          </div>
        </div>
        <div class="library-footer">
          <span>共 {{ filteredDocuments.length }} 份资料</span>
          <span>支持 PDF、DOCX、TXT、MD · 单份文件不超过 10 MB</span>
        </div>
      </section>

      <section v-else class="content-page overview">
        <div class="page-heading">
          <div>
            <span class="eyebrow">WORKSPACE OVERVIEW</span>
            <h1>知识在积累，答案在发生。</h1>
            <p>查看知识库运行状态与问答使用情况。</p>
          </div>
          <button class="secondary-button" @click="refreshData().catch(showError)">
            <Icon name="refresh" :size="17" />
            刷新数据
          </button>
        </div>
        <div class="overview-stats">
          <article
            v-for="item in [
              { label: '知识文档', value: status.documentCount, icon: 'book' },
              { label: '知识片段', value: status.chunkCount, icon: 'grid' },
              { label: '累计回答', value: status.answerCount, icon: 'chat' },
              { label: '有帮助的回答', value: status.helpfulCount, icon: 'up' },
            ]"
            :key="item.label"
          >
            <Icon :name="item.icon" :size="22" />
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </article>
        </div>
        <div class="overview-columns">
          <article class="overview-card">
            <h2>当前运行配置</h2>
            <dl>
              <div>
                <dt>回答模式</dt>
                <dd>{{ status.aiEnabled ? '检索增强生成（RAG）' : '检索原文摘录' }}</dd>
              </div>
              <div>
                <dt>生成模型</dt>
                <dd>{{ status.model || '本地检索' }}</dd>
              </div>
              <div>
                <dt>检索策略</dt>
                <dd>{{ status.embeddingEnabled ? 'BM25 + 向量混合检索' : 'BM25 关键词检索' }}</dd>
              </div>
              <div>
                <dt>已建向量索引</dt>
                <dd>{{ status.indexedChunkCount }} / {{ status.chunkCount }} 个片段</dd>
              </div>
              <div>
                <dt>管理身份</dt>
                <dd>{{ auth.admin ? '管理员' : '访客（只读资料）' }}</dd>
              </div>
            </dl>
            <div class="notice">
              <Icon name="info" :size="18" />
              <p>
                {{
                  status.aiEnabled
                    ? '模型配置已读取，实际可用性以问答调用结果为准。模型不可用时自动返回原文。'
                    : '在 backend/.env 中配置 AI_API_KEY、AI_BASE_URL 和 AI_MODEL，重启后端即可启用模型回答。'
                }}
              </p>
            </div>
          </article>
          <article class="overview-card">
            <h2>从资料到回答</h2>
            <ol class="pipeline">
              <li>
                <span>1</span>
                <div>
                  <strong>文档解析</strong>
                  <p>提取 PDF、Word 与文本文件中的文字。</p>
                </div>
              </li>
              <li>
                <span>2</span>
                <div>
                  <strong>分段与检索</strong>
                  <p>650 字分段，80 字重叠，检索前 5 个相关片段。</p>
                </div>
              </li>
              <li>
                <span>3</span>
                <div>
                  <strong>回答与引用</strong>
                  <p>生成带编号的回答，或直接呈现检索原文。</p>
                </div>
              </li>
              <li>
                <span>4</span>
                <div>
                  <strong>反馈与追溯</strong>
                  <p>保留历史记录、来源快照与回答评价。</p>
                </div>
              </li>
            </ol>
          </article>
        </div>
      </section>
    </main>

    <Modal
      :open="loginOpen"
      :title="adminPortal ? '管理员登录' : registering ? '学生注册' : '学生登录'"
      :busy="loginBusy"
      @close="loginOpen = false"
    >
      <p class="modal-description">
        {{
          adminPortal
            ? '登录后可上传、删除资料和更新知识索引。'
            : '登录后，对话记录将保存到你的账号，重新登录即可查看。'
        }}
      </p>
      <form class="form" @submit.prevent="login">
        <label>
          用户名
          <input
            v-model="username"
            autocomplete="username"
            required
            :maxlength="registering ? 32 : 80"
            :pattern="registering ? '[a-zA-Z0-9_]{4,32}' : undefined"
            :placeholder="registering ? '4—32 位字母、数字或下划线' : '输入用户名'"
          />
        </label>
        <label>
          密码
          <input
            v-model="password"
            type="password"
            :autocomplete="registering ? 'new-password' : 'current-password'"
            required
            :minlength="registering ? 8 : undefined"
            maxlength="64"
          />
        </label>
        <label v-if="registering">
          确认密码
          <input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            required
            minlength="8"
            maxlength="64"
          />
        </label>
        <p v-if="loginError" class="form-error" role="alert">{{ loginError }}</p>
        <button class="primary-button full-width" :disabled="loginBusy">
          <span v-if="loginBusy" class="spinner"></span>
          {{ loginBusy ? '处理中…' : registering ? '注册并登录' : '登录' }}
        </button>
        <button
          v-if="!adminPortal"
          type="button"
          class="text-button"
          :disabled="loginBusy"
          @click="toggleRegistration"
        >
          {{ registering ? '已有账号？去登录' : '没有账号？立即注册' }}
        </button>
        <p class="form-help">
          {{
            adminPortal
              ? '初始管理员账号见项目 README，可通过后端配置修改。'
              : '用户名不区分大小写。密码至少 8 位；访客对话不会自动合并到账号。'
          }}
        </p>
      </form>
    </Modal>

    <Modal :open="uploadOpen" title="添加校园资料" :busy="uploadBusy" @close="uploadOpen = false">
      <p class="modal-description">资料上传后自动解析，回答中会提供文件下载入口。</p>
      <form class="form" @submit.prevent="upload">
        <button
          type="button"
          :class="['drop-zone', { dragging }]"
          :disabled="uploadBusy"
          @click="fileInput.click()"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="chooseFile($event.dataTransfer.files[0])"
        >
          <Icon :name="uploadFile ? 'file' : 'upload'" :size="30" />
          <strong>{{ uploadFile ? uploadFile.name : '点击选择，或拖拽文件到这里' }}</strong>
          <span>
            {{ uploadFile ? size(uploadFile.size) : 'PDF / DOCX / TXT / MD，最大 10 MB' }}
          </span>
        </button>
        <input
          ref="fileInput"
          type="file"
          class="sr-only"
          tabindex="-1"
          accept=".pdf,.docx,.txt,.md"
          @change="chooseFile($event.target.files[0])"
        />
        <label>
          文档标题
          <input v-model="uploadTitle" required maxlength="120" :disabled="uploadBusy" />
        </label>
        <label>
          资料分类
          <select v-model="uploadCategory" :disabled="uploadBusy">
            <option v-for="item in categoryOptions" :key="item">{{ item }}</option>
          </select>
        </label>
        <DocumentSourceFields v-model="uploadSource" :busy="uploadBusy" />
        <p v-if="uploadError" class="form-error" role="alert">{{ uploadError }}</p>
        <div class="form-help">
          上传文件将提供给问答用户下载。文本需为 UTF-8；扫描版 PDF 需先识别文字。{{
            status.embeddingEnabled ? '向量索引可能需要一些时间。' : ''
          }}
        </div>
        <button class="primary-button full-width" :disabled="!uploadFile || uploadBusy">
          <span v-if="uploadBusy" class="spinner"></span>
          <Icon v-else name="upload" :size="17" />
          {{ uploadBusy ? '正在解析并建立索引…' : '上传并加入知识库' }}
        </button>
      </form>
    </Modal>

    <Modal :open="editOpen" title="编辑资料信息" :busy="editBusy" @close="editOpen = false">
      <p class="modal-description">
        修改标题、分类和来源信息。文件原文与检索片段不变；已保存的历史回答仍显示回答当时的来源快照。
      </p>
      <form class="form" @submit.prevent="saveEdit">
        <label>
          文档标题
          <input v-model="editForm.title" required maxlength="120" :disabled="editBusy" />
        </label>
        <label>
          资料分类
          <select v-model="editForm.category" :disabled="editBusy">
            <option v-for="item in categoryOptions" :key="item">{{ item }}</option>
          </select>
        </label>
        <DocumentSourceFields v-model="editForm" :busy="editBusy" />
        <p v-if="editError" class="form-error" role="alert">{{ editError }}</p>
        <div class="form-help">
          发布日期是官网发布时间，不等于文件施行日期；适用范围差异请写在适用说明中，回答会一并呈现。
        </div>
        <button class="primary-button full-width" :disabled="!editForm.title.trim() || editBusy">
          <span v-if="editBusy" class="spinner"></span>
          <Icon v-else name="check" :size="17" />
          {{ editBusy ? '正在保存…' : '保存资料信息' }}
        </button>
      </form>
    </Modal>

    <Modal
      :open="previewOpen"
      :title="preview?.document.title || '资料预览'"
      wide
      @close="closePreview"
    >
      <div v-if="previewLoading" class="loading-state">
        <span class="spinner"></span>
        正在读取资料…
      </div>
      <p v-else-if="previewError" class="form-error" role="alert">{{ previewError }}</p>
      <template v-else-if="preview">
        <div class="preview-meta">
          <span class="category-badge">{{ preview.document.category }}</span>
          <span>{{ preview.document.chunkCount }} 个知识片段</span>
          <span>{{ preview.document.characterCount.toLocaleString() }} 字</span>
          <button v-if="auth.admin" class="text-button" @click="openEdit(preview.document)">
            编辑资料信息
          </button>
        </div>
        <SourceInfo :source="preview.document" />
        <p v-if="auth.admin && !preview.document.sourceUrl" class="demo-warning">
          这份资料还没有登记官网来源，建议补充来源地址、发布单位与适用说明，便于回答时溯源。
        </p>
        <p v-if="preview.document.demo" class="demo-warning">
          这是毕业设计演示资料，内容为虚构示例，请以学校正式文件为准。
        </p>
        <article
          v-for="chunk in preview.chunks"
          :id="`chunk-${chunk.id}`"
          :key="chunk.id"
          class="preview-chunk"
        >
          <div>
            <span>片段 {{ chunk.position }}</span>
            <span>{{ chunk.indexed ? '已建立向量索引' : '可关键词检索' }}</span>
          </div>
          <p>{{ chunk.content }}</p>
        </article>
      </template>
    </Modal>
    <Transition name="toast">
      <div v-if="toast" :class="['toast-message', toast.type]" role="status">
        <Icon :name="toast.type === 'error' ? 'info' : 'check'" :size="18" />
        {{ toast.message }}
        <button class="icon-button" aria-label="关闭提示" @click="toast = null">
          <Icon name="close" :size="15" />
        </button>
      </div>
    </Transition>
  </div>
</template>
