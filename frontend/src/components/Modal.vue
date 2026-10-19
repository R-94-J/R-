<script setup>
import { nextTick, ref, useId, watch } from 'vue'
import Icon from './Icon.vue'
const props = defineProps({ open: Boolean, title: String, wide: Boolean, busy: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
const titleId = useId()
watch(
  () => props.open,
  async (open) => {
    await nextTick()
    if (open && !dialog.value.open) dialog.value.showModal()
    else if (!open && dialog.value.open) dialog.value.close()
  },
  { immediate: true },
)
function close() {
  if (!props.busy) emit('close')
}
</script>

<template>
  <dialog
    ref="dialog"
    :class="['modal', { wide }]"
    :aria-labelledby="titleId"
    @cancel.prevent="close"
    @click="(event) => event.target === dialog && close()"
  >
    <div class="modal-header">
      <h2 :id="titleId">{{ title }}</h2>
      <button class="icon-button" aria-label="关闭弹窗" :disabled="busy" @click="close">
        <Icon name="close" />
      </button>
    </div>
    <div class="modal-body"><slot /></div>
  </dialog>
</template>
