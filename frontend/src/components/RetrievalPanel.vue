<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { api } from '../api'
import Icon from './Icon.vue'
import SourceInfo from './SourceInfo.vue'

defineProps({ embeddingEnabled: Boolean })
const emit = defineEmits(['preview', 'error'])
const query = ref('')
const searchedQuery = ref('')
const busy = ref(false)
const error = ref('')
const result = ref(null)
const elapsedMs = ref(0)
let controller
let requestId = 0

async function search() {
  const question = query.value.trim()
  if (!question || busy.value) return
  const id = ++requestId
  controller = new AbortController()
  busy.value = true
  error.value = ''
  result.value = null
  searchedQuery.value = question
  const start = performance.now()
  try {
    const found = await api('/documents/search', {
      method: 'POST',
      body: { question },
      signal: controller.signal,
    })
    if (id !== requestId) return
    result.value = found
    elapsedMs.value = Math.round(performance.now() - start)
  } catch (failure) {
    if (id !== requestId || failure.name === 'AbortError') return
    error.value = failure.message
    if ([401, 403].includes(failure.status)) emit('error', failure)
  } finally {
    if (id === requestId) busy.value = false
  }
}
function cancel() {
  requestId++
  controller?.abort()
  busy.value = false
  searchedQuery.value = ''
}
onBeforeUnmount(cancel)
</script>

<template>
  <section class="content-page retrieval-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">RETRIEVAL INSPECTION</span>
        <h1>检索调试</h1>
        <p>查看问题命中的原文片段，核对资料和排序。</p>
      </div>
    </div>
    <form class="form retrieval-form" @submit.prevent="search">
      <label>
        待检索问题
        <textarea
          v-model="query"
          rows="3"
          maxlength="600"
          required
          :disabled="busy"
          placeholder="例如：转专业后的学号需要更换吗？"
        ></textarea>
      </label>
      <div class="retrieval-toolbar">
        <span class="muted">
          {{
            embeddingEnabled ? '已启用混合检索，实际策略以返回结果为准' : '当前使用 BM25 关键词检索'
          }}
        </span>
        <button v-if="busy" type="button" class="secondary-button" @click="cancel">取消等待</button>
        <button class="primary-button" :disabled="busy || !query.trim()">
          <span v-if="busy" class="spinner"></span>
          <Icon v-else name="search" :size="16" />
          {{ busy ? '检索中…' : '开始检索' }}
        </button>
      </div>
      <p class="form-help">
        仅检索知识库，不生成回答或写入对话。分数表示相关性，不是正确率；BM25与融合分数不能直接比较。
      </p>
    </form>
    <p v-if="error" class="form-error retrieval-error" role="alert">{{ error }}</p>
    <div v-if="result" class="retrieval-results" aria-live="polite">
      <div class="retrieval-summary">
        <h2>检索结果 · {{ result.sources.length }} 个片段</h2>
        <span>
          {{ result.method === 'hybrid' ? 'BM25 + 向量 / RRF' : 'BM25' }} · 请求耗时
          {{ elapsedMs }} ms
        </span>
      </div>
      <p class="muted">本次问题：{{ searchedQuery }}</p>
      <p v-if="result.degraded" class="form-error" role="status">
        向量检索不可用，本次已降级为关键词检索，请勿把结果记作混合检索成绩。
      </p>
      <div v-if="!result.sources.length" class="empty-state">
        <Icon name="search" :size="30" />
        <h3>未找到相关片段</h3>
        <p>可调整问题用词，或在知识库补充对应资料。</p>
      </div>
      <article
        v-for="(source, index) in result.sources"
        :key="source.chunkId"
        class="retrieval-card"
      >
        <header>
          <span class="source-number">{{ index + 1 }}</span>
          <h3>{{ source.title }}</h3>
          <span class="retrieval-score">排序分数 {{ Number(source.score).toFixed(4) }}</span>
        </header>
        <div class="muted">{{ source.category }} · 片段 {{ source.position }}</div>
        <p class="retrieval-excerpt">{{ source.excerpt }}</p>
        <SourceInfo :source="source" />
        <button class="text-button" @click="emit('preview', source.documentId, source.chunkId)">
          查看完整资料
          <Icon name="arrow" :size="14" />
        </button>
      </article>
    </div>
  </section>
</template>
