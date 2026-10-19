<script setup>
const source = defineModel({ type: Object, required: true })
defineProps({ busy: Boolean })
</script>

<template>
  <label>
    官网来源（选填）
    <input
      v-model="source.sourceUrl"
      type="url"
      maxlength="1000"
      placeholder="https://学校官网/通知页面"
      :disabled="busy"
    />
  </label>
  <label>
    发布单位（选填）
    <input
      v-model="source.publisher"
      maxlength="100"
      placeholder="例如：山东青年政治学院教务处"
      :disabled="busy"
    />
  </label>
  <label>
    官网发布日期（选填）
    <input v-model="source.publishedDate" type="date" :disabled="busy" />
  </label>
  <label>
    适用说明（选填）
    <textarea
      v-model="source.applicability"
      rows="3"
      maxlength="500"
      placeholder="例如：自2025级学生开始执行；以学校最新通知为准"
      :disabled="busy"
    ></textarea>
  </label>
  <label>
    原文件下载链接（选填）
    <input
      v-model="source.downloadUrl"
      type="url"
      maxlength="1000"
      placeholder="留空则提供本系统保存的原文件"
      :disabled="busy"
    />
  </label>
</template>
