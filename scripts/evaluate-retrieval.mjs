// 只评估检索片段，不调用生成模型，不创建对话。标签和原始排名一并保留。
import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { performance } from 'node:perf_hooks'

const datasetBytes = await readFile(new URL('../docs/evaluation/retrieval-cases.json', import.meta.url))
const dataset = JSON.parse(datasetBytes)
const base = process.env.CAMPUS_API_URL || 'http://127.0.0.1:8080/api'
let cookie = '', token
async function request(path, options = {}) {
  const response = await fetch(base + path, {
    ...options, headers: { Cookie: cookie, ...(token ? { [token.headerName]: token.token } : {}), ...options.headers },
  })
  const session = response.headers.get('set-cookie')
  if (session) cookie = session.split(';')[0]
  const data = response.status === 204 ? null : await response.json()
  if (!response.ok) throw new Error(`${response.status} ${data?.message || path}`)
  return data
}
const normalize = text => text.replace(/\s+/g, '')
const results = []
token = await request('/auth/csrf')
await request('/auth/login', { method: 'POST', body: new URLSearchParams({
  username: process.env.ADMIN_USERNAME || 'admin', password: process.env.ADMIN_PASSWORD || 'Campus@2026',
}) })
token = await request('/auth/csrf')
try {
  const documents = await request('/documents')
  for (const item of dataset.cases) {
    if (!documents.some(doc => doc.sourceUrl === item.sourceUrl)) throw new Error(`缺少标准资料 ${item.sourceUrl}，请先导入学校归档资料`)
  }
  for (const item of dataset.cases) {
    const start = performance.now()
    const found = await request('/documents/search', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question: item.question }) })
    const rank = found.sources.slice(0, 5).findIndex(source => source.sourceUrl === item.sourceUrl && item.evidence.every(text => normalize(source.excerpt).includes(normalize(text)))) + 1
    results.push({ ...item, rank, hit: rank > 0, reciprocalRank: rank ? 1 / rank : 0, elapsedMs: Math.round(performance.now() - start), ...found })
    console.log(`${item.id}: ${found.method}, ${rank ? `证据排名 ${rank}` : '前5名未命中标准片段'}`)
  }
  const times = results.map(item => item.elapsedMs).sort((a,b) => a-b)
  const report = {
    evaluatedAt: new Date().toISOString(), dataset: dataset.name, version: dataset.version,
    datasetHash: createHash('sha256').update(datasetBytes).digest('hex'),
    corpus: documents.map(({ id, title, sourceUrl, contentHash, chunkCount }) => ({ id, title, sourceUrl, contentHash, chunkCount })),
    summary: { count: results.length, hitAt5: results.filter(item => item.hit).length / results.length,
      mrrAt5: results.reduce((sum,item) => sum + item.reciprocalRank,0) / results.length,
      meanMs: times.reduce((sum,item) => sum + item,0) / times.length, p95Ms: times[Math.ceil(times.length * 0.95) - 1],
      degradedQueries: results.filter(item => item.degraded).length },
    limitations: dataset.description, results,
  }
  await writeFile(new URL('../docs/evaluation/retrieval-results.json', import.meta.url), JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify(report.summary, null, 2))
} finally { await request('/auth/logout', { method: 'POST' }) }
