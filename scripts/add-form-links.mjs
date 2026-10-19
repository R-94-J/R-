// Preserve official Word attachment URLs without claiming their captcha-protected content was parsed.
import { readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
const root = new URL('../school-data/sdyu/', import.meta.url)
const manifest = JSON.parse(await readFile(new URL('manifest.json', root), 'utf8'))
for (const id of ['retake-form-2026', 'grade-review-flow-2025', 'credit-transfer-form-2025']) {
  const entry = manifest.find(item => item.id === id)
  const html = await readFile(new URL(`originals/${id}.html`, root), 'utf8')
  const match = html.match(/href="([^"]*download\.jsp[^\"]*)"[^>]*>([^<]*\.docx)<\/a>/i)
  if (!match) throw new Error(`Attachment link not found: ${id}`)
  const downloadUrl = new URL(match[1].replaceAll('&amp;', '&'), entry.url).href
  const text = `# ${entry.title} · Word下载指引\n\n官网来源：${entry.url}\n官网发布日期：${entry.publishedDate}\n\n学校官网提供《${entry.title}》的Word附件：${match[2]}。\n\n下载链接：${downloadUrl}\n\n打开官网附件下载链接后可能需要输入验证码。该条资料只记录经官网核对的下载入口，尚未解析附件正文；不能据此推断申请条件、审批程序或表格填写要求。\n`
  const path = `texts/${id}-download.md`
  await writeFile(new URL(path, root), text)
  entry.files = entry.files.filter(file => file.path !== path)
  entry.files.push({ path, kind: 'download-guide', bytes: Buffer.byteLength(text), sha256: createHash('sha256').update(text).digest('hex') })
  entry.importFile = path; entry.status = 'ready'; entry.downloadUrl = downloadUrl
  entry.scope = '仅提供官网Word附件下载入口；附件需官网验证码，未解析正文，具体要求请下载后查看。'
  console.log(entry.title, downloadUrl)
}
await writeFile(new URL('manifest.json', root), JSON.stringify(manifest, null, 2) + '\n')
