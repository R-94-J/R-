import { test, expect } from '@playwright/test'

test('real school documents retain dates, official links and question evidence', async ({
  page,
}) => {
  test.setTimeout(180000)
  await page.goto('/admin')
  const login = page.getByRole('dialog', { name: '管理员登录' })
  await login.getByLabel('用户名').fill(process.env.E2E_ADMIN_USERNAME || 'admin')
  await login.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD || 'Campus@2026')
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login).not.toBeVisible()
  const documents = await (await page.request.get('/api/documents')).json()
  expect(
    documents.filter((doc) => doc.sourceUrl?.includes('sdyu.edu.cn')).length,
  ).toBeGreaterThanOrEqual(16)
  expect(
    documents.filter((doc) => doc.fileType === 'pdf' && doc.sourceUrl?.includes('xsc.sdyu.edu.cn')),
  ).toHaveLength(6)
  expect(documents.filter((doc) => doc.demo)).toHaveLength(0)
  await page
    .getByRole('navigation')
    .getByRole('button', { name: /知识库/ })
    .click()
  await page.getByLabel('搜索文档').fill('转专业管理办法')
  await page
    .getByRole('row')
    .filter({ hasText: '全日制本专科学生转专业管理办法' })
    .getByRole('button', { name: '查看', exact: true })
    .click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.locator('.provenance-card')).toContainText('2025-09-03')
  await expect(dialog.locator('.provenance-card')).toContainText('自2025级学生开始执行')
  await expect(dialog.getByRole('link', { name: /查看官网原文/ })).toHaveAttribute(
    'href',
    'https://jwc.sdyu.edu.cn/info/1202/10313.htm',
  )
  await dialog.getByLabel('关闭弹窗').click()
  await page.getByRole('button', { name: /开启新对话/ }).click()
  await page.getByRole('button', { name: /学士学位需要多少绩点/ }).click()
  await expect(page.locator('.message.assistant .message-content')).toContainText('1.8', {
    timeout: 60000,
  })
  await expect(
    page
      .locator('.message.assistant .provenance-card')
      .filter({ hasText: '学士学位授予管理办法' })
      .getByRole('link', { name: /查看官网原文/ }),
  ).toHaveAttribute('href', 'https://jwc.sdyu.edu.cn/info/1202/9950.htm')
  await expect(page.locator('.thinking')).toHaveCount(0)
  await page.screenshot({ path: 'test-results/sdyu-answer.png', fullPage: true })
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const histories = await (await page.request.get('/api/conversations')).json()
  for (const item of histories.filter((c) =>
    c.title.startsWith('山东青年政治学院2025年学士学位授予管理办法'),
  ))
    await page.request.delete(`/api/conversations/${item.id}`, {
      headers: { [csrf.headerName]: csrf.token },
    })
})

test('student portal is question-only and admin logout removes management access', async ({
  page,
}) => {
  await page.goto('/student')
  await expect(page.getByLabel('输入校园问题')).toBeEnabled()
  await expect(page.getByRole('navigation').getByRole('button')).toHaveCount(1)
  await expect(page.getByRole('button', { name: /上传资料|系统概览|查看完整资料/ })).toHaveCount(0)
  expect((await page.request.get('/api/documents')).status()).toBe(401)
  expect((await page.request.get('/api/status')).status()).toBe(401)
  await page.getByRole('link', { name: '管理员入口 →' }).click()
  const login = page.getByRole('dialog', { name: '管理员登录' })
  await login.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD || 'Campus@2026')
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toBeVisible()
  await page.getByRole('button', { name: /管理员端 · 点击退出/ }).click()
  await expect(page.getByRole('heading', { name: '校园知识库 · 管理员端' })).toBeVisible()
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toHaveCount(0)
  expect((await page.request.get('/api/documents')).status()).toBe(401)
  await page.goto('/student')
  await page.getByLabel('输入校园问题').fill('家庭经济困难学生认定分为哪些档次？')
  await page.getByLabel('发送问题').click()
  await expect(page.locator('.message.assistant .sources')).toContainText(
    '家庭经济困难学生认定办法',
    { timeout: 60000 },
  )
  await expect(page.getByRole('button', { name: /查看完整资料/ })).toHaveCount(0)
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const histories = await (await page.request.get('/api/conversations')).json()
  for (const item of histories)
    await page.request.delete(`/api/conversations/${item.id}`, {
      headers: { [csrf.headerName]: csrf.token },
    })
})
