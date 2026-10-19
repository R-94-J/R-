<script setup>
import { ref } from 'vue'
import { api, download } from '../api'
import Modal from './Modal.vue'
import Icon from './Icon.vue'

const props = defineProps({ conversation: { type: Object, required: true }, disabled: Boolean })
const emit = defineEmits(['renamed', 'error', 'notify'])
const open = ref(false)
const title = ref('')
const saving = ref(false)
const exporting = ref(false)
const error = ref('')

function edit() {
  title.value = props.conversation.title
  error.value = ''
  open.value = true
}
async function save() {
  if (saving.value || !title.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    const updated = await api(`/conversations/${props.conversation.id}`, {
      method: 'PATCH',
      body: { title: title.value.trim() },
    })
    emit('renamed', updated)
    open.value = false
    emit('notify', '对话标题已保存')
  } catch (failure) {
    error.value = failure.message
    if ([401, 403, 404].includes(failure.status)) emit('error', failure)
  } finally {
    saving.value = false
  }
}
async function exportChat() {
  if (exporting.value) return
  exporting.value = true
  try {
    const filename = props.conversation.title
      .replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
      .slice(0, 60)
    await download(`/conversations/${props.conversation.id}/export`, `知校园-${filename}.md`)
    emit('notify', '已导出对话及来源快照')
  } catch (failure) {
    emit('error', failure)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="conversation-actions">
    <button class="text-button" :disabled="disabled || saving || exporting" @click="edit">
      <Icon name="edit" :size="15" />
      重命名对话
    </button>
    <button class="text-button" :disabled="disabled || saving || exporting" @click="exportChat">
      <span v-if="exporting" class="spinner"></span>
      <Icon v-else name="download" :size="15" />
      {{ exporting ? '正在导出…' : '导出对话' }}
    </button>
  </div>
  <Modal :open="open" title="重命名对话" :busy="saving" @close="open = false">
    <form class="form" @submit.prevent="save">
      <label>
        对话标题
        <input v-model="title" required maxlength="80" :disabled="saving" />
      </label>
      <p class="form-help">使用便于查找的标题，例如“学位申请材料”。最多80字。</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="primary-button full-width" :disabled="saving || !title.trim()">
        {{ saving ? '正在保存…' : '保存标题' }}
      </button>
    </form>
  </Modal>
</template>
