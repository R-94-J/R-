import { test, expect } from '@playwright/test'

test.afterEach(async ({ page }) => {
  const csrfResponse = await page.request.get('/api/auth/csrf')
  if (!csrfResponse.ok()) return
  const csrf = await csrfResponse.json()
  const histories = await (await page.request.get('/api/conversations')).json()
  for (const conversation of histories.filter((item) =>
    ['实验中心预约在哪里办理？', '我是学生，推荐明天的彩票号码'].includes(item.title),
  )) {
    await page.request.delete(`/api/conversations/${conversation.id}`, {
      headers: { [csrf.headerName]: csrf.token },
    })
  }
})

test('MySQL backed document, chat, citations, feedback and deletion workflow', async ({ page }) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/admin')
  const login = page.getByRole('dialog', { name: '管理员登录' })
  await login.getByLabel('用户名').fill(process.env.E2E_ADMIN_USERNAME || 'admin')
  await login.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD || 'Campus@2026')
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login).not.toBeVisible()
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toBeVisible()
  await page
    .getByRole('navigation')
    .getByRole('button', { name: /知识库/ })
    .click()
  await page.getByRole('button', { name: '上传资料', exact: true }).click()
  const upload = page.getByRole('dialog', { name: '添加校园资料' })
  const title = `实验中心预约指南 E2E ${Date.now()}`
  const text =
    '# 实验中心预约指南\n实验中心预约需要提前两天申请，携带学生证到明理楼305室办理。每次预约最多三小时。\n' +
    '实验中心的设备应当按使用规范操作，使用结束后需要登记设备状态。\n'.repeat(40)
  await upload.locator('input[type=file]').setInputFiles({
    name: 'guide.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from(`${text}\n测试批次：${title}`),
  })
  await upload.getByLabel('文档标题').fill(title)
  await upload.getByRole('button', { name: '上传并加入知识库' }).click()
  await expect(upload).not.toBeVisible({ timeout: 30000 })
  await page.getByLabel('搜索文档').fill(title)
  const row = page.getByRole('row').filter({ hasText: title })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '查看', exact: true }).click()
  const preview = page.getByRole('dialog', { name: title })
  await expect(preview).toContainText('明理楼305室')
  await preview.getByLabel('关闭弹窗').click()
  await page.getByRole('button', { name: /开启新对话/ }).click()
  await page.getByLabel('输入校园问题').fill('实验中心预约在哪里办理？')
  await page.getByLabel('发送问题').click()
  const answer = page.locator('.message.assistant').first()
  await expect(answer).toContainText('明理楼305室', { timeout: 30000 })
  await expect(answer.locator('.source-card').first()).toContainText(title)
  const downloadLink = answer.getByRole('link', { name: `下载文件：${title} ↓` })
  await expect(downloadLink).toHaveAttribute('href', /\/api\/files\//)
  const downloaded = await page.request.get(await downloadLink.getAttribute('href'))
  expect(downloaded.ok()).toBeTruthy()
  expect((await downloaded.body()).toString('utf8')).toBe(`${text}\n测试批次：${title}`)
  await answer.getByLabel('回答有帮助').click()
  await expect(answer.getByLabel('回答有帮助')).toHaveAttribute('aria-pressed', 'true')
  await answer.locator('.source-card summary').first().click()
  await answer
    .locator('.source-card')
    .first()
    .getByRole('button', { name: /查看完整资料/ })
    .click()
  await expect(page.getByRole('dialog', { name: title })).toContainText('明理楼305室')
  await page.getByRole('dialog', { name: title }).getByLabel('关闭弹窗').click()
  await page.screenshot({ path: 'test-results/chat-desktop.png', fullPage: true })
  await page.reload()
  await expect(page.getByRole('button', { name: '上传资料', exact: true })).toBeVisible()
  await page.locator('.conversation-link').filter({ hasText: '实验中心预约在哪里办理？' }).click()
  await expect(page.locator('.message.assistant').first()).toContainText('明理楼305室')
  await expect(page.getByLabel('回答有帮助')).toHaveAttribute('aria-pressed', 'true')
  await page
    .getByRole('navigation')
    .getByRole('button', { name: /知识库/ })
    .click()
  await page.getByLabel('搜索文档').fill(title)
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByLabel(`删除文档 ${title}`, { exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: title })).toHaveCount(0)
  await page.getByRole('button', { name: /开启新对话/ }).click()
  await page.getByLabel('输入校园问题').fill('我是学生，推荐明天的彩票号码')
  await page.getByLabel('发送问题').click()
  await expect(page.locator('.message.assistant')).toContainText('超出服务范围', { timeout: 60000 })
  await page.getByRole('navigation').getByRole('button', { name: '系统概览' }).click()
  await expect(page.getByRole('heading', { name: '当前运行配置' })).toBeVisible()
  await page.screenshot({ path: 'test-results/overview-desktop.png', fullPage: true })
  // Remove only the conversations created by this browser session.
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const histories = await (await page.request.get('/api/conversations')).json()
  for (const conversation of histories.filter((item) =>
    ['实验中心预约在哪里办理？', '我是学生，推荐明天的彩票号码'].includes(item.title),
  )) {
    const result = await page.request.delete(`/api/conversations/${conversation.id}`, {
      headers: { [csrf.headerName]: csrf.token },
    })
    expect(result.ok()).toBeTruthy()
  }
  expect(errors).toEqual([])
})

test('mobile layout and navigation remain usable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.getByLabel('输入校园问题')).toBeEnabled()
  await expect(page.getByLabel('输入校园问题')).toBeInViewport()
  await page.screenshot({ path: 'test-results/home-mobile.png', fullPage: true })
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy()
  await page.getByLabel('打开导航').click()
  await expect(page.getByRole('navigation').getByRole('button')).toHaveCount(1)
  await expect(page.getByRole('navigation').getByRole('button', { name: '知识问答' })).toBeVisible()
  await expect(page.getByLabel('搜索文档')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '管理员入口 →' })).toBeVisible()
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBeTruthy()
})
