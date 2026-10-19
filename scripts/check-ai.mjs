// Run from any directory: node scripts/check-ai.mjs
// Reads the local config without printing the API key.
import { readFile } from 'node:fs/promises'
const content = await readFile(new URL('../backend/.env', import.meta.url), 'utf8')
const config = Object.fromEntries(content.split(/\r?\n/).filter(line => line.trim() && !line.trim().startsWith('#')).map(line => {
  const index = line.indexOf('=')
  return [line.slice(0, index).trim(), line.slice(index + 1).trim()]
}))
const key = process.env.AI_API_KEY || config.AI_API_KEY
const base = (process.env.AI_BASE_URL || config.AI_BASE_URL || 'https://api.openai.com/v1').replace(/\/+$/, '')
const model = process.env.AI_MODEL || config.AI_MODEL || 'gpt-4o-mini'
if (!key) { console.error('AI_API_KEY is not configured.'); process.exit(1) }
console.log('API:', base, 'Model:', model)
try {
  const response = await fetch(`${base}/chat/completions`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model, temperature: 0.2, max_tokens: 1600, messages: [
      { role: 'system', content: '请仅依据资料回答并带引用 [1]。资料[1]：演示图书馆每人可借10本书，借期30天。' },
      { role: 'user', content: '可以借几本书，借期多久？' },
    ] }),
    signal: AbortSignal.timeout(45000),
  })
  const raw = await response.text()
  let data
  try { data = JSON.parse(raw) }
  catch {
    console.error(JSON.stringify({ status: response.status, contentType: response.headers.get('content-type'), responsePreview: raw.slice(0, 600).replaceAll(key, '[REDACTED]') }, null, 2))
    process.exit(1)
  }
  const output = response.ok ? { status: response.status, answer: data.choices?.[0]?.message?.content, finishReason: data.choices?.[0]?.finish_reason }
    : { status: response.status, error: data.error?.message || data.message || 'Provider rejected the request' }
  console.log(JSON.stringify(output, null, 2).replaceAll(key, '[REDACTED]'))
  if (!response.ok || !output.answer) process.exitCode = 1
} catch (error) {
  console.error('Request failed:', error.cause?.code || error.name)
  process.exitCode = 1
}
