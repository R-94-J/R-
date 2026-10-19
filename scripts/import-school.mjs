// Import the downloaded, verified school corpus through the authenticated application API.
import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { Blob } from 'node:buffer'
const root = new URL('../school-data/sdyu/', import.meta.url)
const manifest = JSON.parse(await readFile(new URL('manifest.json', root), 'utf8'))
const env = Object.fromEntries((await readFile(new URL('../backend/.env', import.meta.url), 'utf8')).split(/\r?\n/)
  .filter(line => line.includes('=') && !line.trim().startsWith('#')).map(line => { const i = line.indexOf('='); return [line.slice(0, i).trim(), line.slice(i + 1).trim()] }))
const base = process.env.CAMPUS_API_URL || 'http://127.0.0.1:8080/api'
let cookie = '', token
async function request(path, options = {}) {
  const response = await fetch(base + path, { ...options, headers: { Cookie: cookie, ...(token ? { [token.headerName]: token.token } : {}), ...options.headers } })
  const session = response.headers.get('set-cookie')
  if (session) cookie = session.split(';')[0]
  const data = response.status === 204 ? null : await response.json()
  if (!response.ok) throw new Error(`${response.status} ${data?.message || path}`)
  return data
}
token = await request('/auth/csrf')
await request('/auth/login', { method: 'POST', body: new URLSearchParams({ username: process.env.ADMIN_USERNAME || env.ADMIN_USERNAME || 'admin', password: process.env.ADMIN_PASSWORD || env.ADMIN_PASSWORD || 'Campus@2026' }) })
token = await request('/auth/csrf')
const report = []
let failures = 0
try {
  const documents = await request('/documents')
  for (const entry of manifest.filter(item => item.status === 'ready')) {
    try {
      const file = entry.files.find(item => item.path === entry.importFile)
      const bytes = await readFile(new URL(file.path, root))
      if (createHash('sha256').update(bytes).digest('hex') !== file.sha256) throw new Error('Local file checksum mismatch')
      let document = documents.find(doc => doc.sourceUrl === entry.url)
      if (!document) {
        const form = new FormData()
        form.set('file', new Blob([bytes]), entry.importFile.split('/').pop())
        form.set('title', entry.title); form.set('category', entry.category)
        document = await request('/documents', { method: 'POST', body: form })
      }
      await request(`/documents/${document.id}/provenance`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
        sourceUrl: entry.url, publisher: `山东青年政治学院${entry.publisher}`, publishedDate: entry.publishedDate,
        downloadUrl: entry.downloadUrl || entry.files.find(file => file.kind === 'attachment' && file.url)?.url,
        applicability: entry.scope || '依据官网公开原文；具体适用范围及效力以原文和学校最新通知为准。',
      }) })
      // Also attach the verified official URL to an existing user-uploaded copy of the same form.
      if (entry.downloadUrl) {
        for (const copy of documents.filter(doc => !doc.sourceUrl &&
            (doc.title === entry.title || doc.title === entry.title + '申请表'))) {
          await request(`/documents/${copy.id}/provenance`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
            sourceUrl: entry.url, publisher: `山东青年政治学院${entry.publisher}`, publishedDate: entry.publishedDate,
            applicability: '用户上传文件，关联同名官网附件下载入口；具体内容以官方文件为准。', downloadUrl: entry.downloadUrl,
          }) })
          console.log('LINKED EXISTING FORM', copy.title)
        }
      }
      if (entry.importFile.endsWith('.pdf')) {
        const detail = await request(`/documents/${document.id}`)
        const text = `# ${entry.title}\n\n来源：${entry.url}\n官网发布日期：${entry.publishedDate}\n适用说明：${entry.scope || '以文件原文为准'}\n文件由用户提供；以下为 PDF 解析片段，相邻片段包含重叠文字。\n\n`
          + detail.chunks.map(chunk => `## 片段 ${chunk.position}\n\n${chunk.content}`).join('\n\n')
        await writeFile(new URL(`texts/${entry.id}-pdf-extracted.md`, root), text + '\n')
      }
      report.push({ id: entry.id, documentId: document.id, title: entry.title, sourceUrl: entry.url, chunkCount: document.chunkCount, status: 'imported' })
      console.log('IMPORTED', entry.title, document.chunkCount, 'chunks')
    } catch (error) { failures++; report.push({ id: entry.id, status: 'failed', error: error.message }); console.error('FAILED', entry.id, error.message) }
  }
  // Preserve a backup before replacing only the built-in fictional demo records.
  if (!failures && report.length > 0 && process.argv.includes('--replace-demo')) {
    const demos = documents.filter(doc => doc.demo)
    const backup = []
    for (const doc of demos) backup.push(await request(`/documents/${doc.id}`))
    await writeFile(new URL('demo-backup.json', root), JSON.stringify(backup, null, 2) + '\n')
    for (const doc of demos) { await request(`/documents/${doc.id}`, { method: 'DELETE' }); console.log('REPLACED DEMO', doc.title) }
  }
  await writeFile(new URL('import-report.json', root), JSON.stringify({ importedAt: new Date().toISOString(), documents: report }, null, 2) + '\n')
} finally { await request('/auth/logout', { method: 'POST' }) }
if (failures) process.exitCode = 1
