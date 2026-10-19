<script setup>
defineProps({ source: { type: Object, required: true } })
function safeUrl(value) {
  if (typeof value === 'string' && /^\/api\/files\/[a-f0-9-]{36}$/.test(value)) return value
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null
  } catch {
    return null
  }
}
</script>
<template>
  <div
    v-if="
      source.sourceUrl ||
      source.downloadUrl ||
      source.publisher ||
      source.publishedDate ||
      source.applicability
    "
    class="provenance-card"
  >
    <div>
      <strong>{{ source.publisher || '资料来源' }}</strong>
      <span v-if="source.publishedDate">官网发布：{{ source.publishedDate }}</span>
    </div>
    <a
      v-if="safeUrl(source.sourceUrl)"
      :href="safeUrl(source.sourceUrl)"
      target="_blank"
      rel="noopener noreferrer"
    >
      {{ source.title }} · 查看官网原文 ↗
    </a>
    <a
      v-if="safeUrl(source.downloadUrl)"
      :href="safeUrl(source.downloadUrl)"
      target="_blank"
      rel="noopener noreferrer"
      class="download-link"
    >
      下载文件：{{ source.title }} ↓
    </a>
    <p v-if="source.applicability">{{ source.applicability }}</p>
  </div>
</template>
