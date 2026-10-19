<script setup>
defineProps({ name: String, size: { type: [Number, String], default: 20 } })
const paths = {
  book: 'M3 4c3-1 6 0 9 2 3-2 6-3 9-2v15c-3-1-6 0-9 2-3-2-6-3-9-2V4Zm9 2v15',
  chat: 'M21 11a8 8 0 0 1-8 8H8l-5 3V11a9 9 0 1 1 18 0ZM7 10h10M7 14h6',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  chart: 'M4 3v17h17M8 15v-4M13 15V6M18 15V9',
  plus: 'M12 5v14M5 12h14',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13',
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  file: 'M14 2H5v20h14V7l-5-5ZM14 2v6h5M8 12h8M8 16h6',
  upload: 'M12 16V3M7 8l5-5 5 5M4 15v6h16v-6',
  download: 'M12 3v13M7 11l5 5 5-5M4 16v5h16v-5',
  edit: 'm16 3 5 5-12 12H4v-5L16 3ZM13 6l5 5',
  close: 'm6 6 12 12M18 6 6 18',
  trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7',
  shield: 'm12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6l9-4ZM8 12l3 3 5-6',
  spark: 'm12 3 2.6 6.4L21 12l-6.4 2.6L12 21l-2.6-6.4L3 12l6.4-2.6L12 3ZM21 2v4M19 4h4',
  clock: 'M12 7v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  check: 'm5 12 4 4L19 6',
  user: 'M20 21v-2a7 7 0 0 0-14 0v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  logout: 'M9 3H3v18h6M9 12h12M16 7l5 5-5 5',
  menu: 'M4 6h16M4 12h16M4 18h16',
  copy: 'M9 9h12v12H9zM5 15H3V3h12v2',
  up: 'M7 10H3v11h4M7 10l5-8c3 0 2 6 2 6h5a2 2 0 0 1 2 3l-2 8a2 2 0 0 1-2 2H7V10Z',
  down: 'M7 14H3V3h4M7 14l5 8c3 0 2-6 2-6h5a2 2 0 0 0 2-3l-2-8a2 2 0 0 0-2-2H7v11Z',
  info: 'M12 11v6M12 7h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  refresh: 'M20 7A9 9 0 1 0 21 15M20 2v6h-6',
  cap: 'm2 9 10-5 10 5-10 5-10-5ZM6 11v6c4 3 8 3 12 0v-6M22 9v8',
}
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.file" />
  </svg>
</template>
