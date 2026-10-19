import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
const root = new URL('../school-data/sdyu/', import.meta.url)
const manifest = JSON.parse(await readFile(new URL('manifest.json', root), 'utf8'))
for (const entry of manifest) for (const file of entry.files) {
  const data = await readFile(new URL(file.path, root))
  if (createHash('sha256').update(data).digest('hex') !== file.sha256) throw new Error(`Checksum mismatch: ${file.path}`)
}
const base = 'http://127.0.0.1:8080/api'
const csrfResponse = await fetch(base + '/auth/csrf')
const cookie = csrfResponse.headers.get('set-cookie').split(';')[0]
const token = await csrfResponse.json()
const headers = { Cookie: cookie, 'Content-Type': 'application/json', [token.headerName]: token.token }
const questions = [
  { question: '山东青年政治学院2025年学士学位授予管理办法规定，平均学分绩点要达到多少？', expected: '1.8', url: 'https://jwc.sdyu.edu.cn/info/1202/9950.htm' },
  { question: '2025级学生申请转专业需要修满几学期？什么时候报名？', expected: '一学期', url: 'https://jwc.sdyu.edu.cn/info/1202/10313.htm' },
  { question: '图书馆信息共享空间预约后，应该在多少分钟内刷卡登记？', expected: '30', url: 'https://lib.sdyu.edu.cn/info/1078/3240.htm' },
]
const results = []
for (const item of questions) {
  const response = await fetch(base + '/chat', { method: 'POST', headers, body: JSON.stringify({ question: item.question }) })
  const result = await response.json()
  const message = result.assistantMessage
  const passed = response.ok && message.sources.some(source => source.sourceUrl === item.url && source.excerpt.includes(item.expected))
  results.push({ question: item.question, passed, mode: message?.mode, latencyMs: message?.latencyMs,
    sources: message?.sources.map(s => ({ title: s.title, url: s.sourceUrl, publishedDate: s.publishedDate, applicability: s.applicability })) })
  console.log(JSON.stringify(results.at(-1), null, 2))
  if (result.conversationId) await fetch(`${base}/conversations/${result.conversationId}`, { method: 'DELETE', headers })
}
await writeFile(new URL('verification.json', root), JSON.stringify({ verifiedAt: new Date().toISOString(), fileHashesValid: true, results }, null, 2) + '\n')
if (results.some(result => !result.passed)) process.exitCode = 1
