import { test, expect } from '@playwright/test'

async function adminSession(browser, baseURL) {
  const context = await browser.newContext({ baseURL })
  const request = context.request
  const csrf = await (await request.get('/api/auth/csrf')).json()
  const login = await request.post('/api/auth/login', {
    headers: { [csrf.headerName]: csrf.token },
    form: {
      username: process.env.E2E_ADMIN_USERNAME || 'admin',
      password: process.env.E2E_ADMIN_PASSWORD || 'Campus@2026',
    },
  })
  expect(login.ok()).toBeTruthy()
  const fresh = await (await request.get('/api/auth/csrf')).json()
  return { context, request, headers: { [fresh.headerName]: fresh.token } }
}

async function uploadFixture(admin, title) {
  const response = await admin.request.post('/api/documents', {
    headers: admin.headers,
    multipart: {
      file: {
        name: 'history-guide.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from(
          `${title}：图书馆课题讨论室预约需要提前两天提交申请，携带学生证到明理楼305室办理。`,
        ),
      },
      title,
      category: '学习资源',
      sourceUrl: 'https://lib.sdyu.edu.cn/history-test.htm',
      publisher: '图书馆',
      publishedDate: '2025-03-01',
      applicability: '仅用于功能测试',
    },
  })
  expect(response.ok()).toBeTruthy()
  return response.json()
}

test('student can find, rename and export an owned conversation with source snapshots', async ({
  page,
  browser,
  baseURL,
}) => {
  const admin = await adminSession(browser, baseURL)
  const title = `图书馆课题讨论室测试 ${Date.now()}`
  const doc = await uploadFixture(admin, title)
  let conversationId
  try {
    await page.goto('/student')
    await page.getByLabel('输入校园问题').fill(`${title}在哪里办理预约？`)
    const response = page.waitForResponse(
      (r) => r.url().endsWith('/api/chat') && r.request().method() === 'POST',
    )
    await page.getByLabel('发送问题').click()
    const answer = await (await response).json()
    conversationId = answer.conversationId
    await expect(page.getByRole('button', { name: '重命名对话', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '重命名对话', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '重命名对话' })
    await dialog.getByLabel('对话标题').fill('  图书馆预约备忘  ')
    await dialog.getByRole('button', { name: '保存标题' }).click()
    await expect(dialog).not.toBeVisible()
    await expect(page.locator('.conversation-bar > h2')).toHaveText('图书馆预约备忘')
    await page.getByLabel('搜索对话标题').fill('不存在的标题')
    await expect(page.locator('.conversation-link')).toHaveCount(0)
    await expect(page.locator('.history-empty')).toContainText('没有匹配')
    await page.getByLabel('清空对话搜索').click()
    await page.reload()
    await page.getByLabel('搜索对话标题').fill('预约备忘')
    await page.locator('.conversation-link').filter({ hasText: '图书馆预约备忘' }).click()
    await expect(page.locator('.message.assistant')).toContainText('明理楼305室')
    // Removing the original must not remove the saved excerpt or source metadata from exports.
    await admin.request.delete(`/api/documents/${doc.id}`, { headers: admin.headers })
    const downloading = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出对话', exact: true }).click()
    const file = await downloading
    expect(file.suggestedFilename()).toBe('知校园-图书馆预约备忘.md')
    const stream = await file.createReadStream()
    const parts = []
    for await (const chunk of stream) parts.push(chunk)
    const text = Buffer.concat(parts).toString('utf8')
    expect(text).toContain('图书馆预约备忘')
    expect(text).toContain(title)
    expect(text).toContain('明理楼305室')
    expect(text).toContain('https://lib.sdyu.edu.cn/history-test.htm')
    expect(text).toContain('2025-03-01')
    expect(text).toContain('仅用于功能测试')
    expect((await admin.request.get(`/api/conversations/${conversationId}/export`)).status()).toBe(
      404,
    )
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByRole('button', { name: '导出对话', exact: true })).toBeInViewport()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy()
  } finally {
    if (conversationId) {
      const csrf = await (await page.request.get('/api/auth/csrf')).json()
      await page.request.delete(`/api/conversations/${conversationId}`, {
        headers: { [csrf.headerName]: csrf.token },
      })
    }
    await admin.request.delete(`/api/documents/${doc.id}`, { headers: admin.headers })
    await admin.context.close()
  }
})

test('admin can inspect retrieval evidence, empty results and recover from an error', async ({
  browser,
  baseURL,
}) => {
  const admin = await adminSession(browser, baseURL)
  const title = `图书馆检索调试 ${Date.now()}`
  const doc = await uploadFixture(admin, title)
  const page = await admin.context.newPage()
  try {
    await page.goto('/admin')
    await page.getByRole('navigation').getByRole('button', { name: '检索调试' }).click()
    await page.getByLabel('待检索问题').fill(`${title}预约提前多久申请？`)
    const before = await (await page.request.get('/api/conversations')).json()
    await page.getByRole('button', { name: '开始检索' }).click()
    const card = page.locator('.retrieval-card').filter({ hasText: title })
    await expect(card.first()).toContainText('提前两天')
    await expect(page.locator('.retrieval-summary')).toContainText('BM25')
    await expect(card.first()).toContainText('排序分数')
    await card.first().getByRole('button', { name: '查看完整资料' }).click()
    await expect(page.getByRole('dialog', { name: title })).toContainText('明理楼305室')
    await page.getByRole('dialog', { name: title }).getByLabel('关闭弹窗').click()
    expect(await (await page.request.get('/api/conversations')).json()).toEqual(before)
    await page.route(
      '**/api/documents/search',
      (route) => route.fulfill({ status: 503, json: { message: '检索服务测试错误' } }),
      { times: 1 },
    )
    await page.getByRole('button', { name: '开始检索' }).click()
    await expect(page.getByRole('alert')).toContainText('检索服务测试错误')
    await expect(page.locator('.retrieval-card')).toHaveCount(0)
    await page.getByRole('button', { name: '开始检索' }).click()
    await expect(card.first()).toBeVisible()
    await page.getByLabel('待检索问题').fill('qzvxunmatchedword123456789')
    await page.getByRole('button', { name: '开始检索' }).click()
    await expect(page.getByRole('heading', { name: '未找到相关片段' })).toBeVisible()
    await page.route(
      '**/api/documents/search',
      (route) =>
        route.fulfill({
          json: { sources: [], method: 'bm25', degraded: true },
        }),
      { times: 1 },
    )
    await page.getByRole('button', { name: '开始检索' }).click()
    await expect(page.getByRole('status')).toContainText('已降级为关键词检索')
    // 取消的旧请求即使晚于新请求结束，也不能替换新结果。
    let release
    const waiting = new Promise((resolve) => {
      release = resolve
    })
    await page.route(
      '**/api/documents/search',
      async (route) => {
        await waiting
        await route
          .fulfill({ json: { sources: [], method: 'hybrid', degraded: false } })
          .catch(() => {})
      },
      { times: 1 },
    )
    const pendingRequest = page.waitForRequest('**/api/documents/search')
    await page.getByRole('button', { name: '开始检索' }).click()
    await pendingRequest
    await page.getByRole('button', { name: '取消等待' }).click()
    await expect(page.getByRole('button', { name: '开始检索' })).toBeEnabled()
    await expect(page.locator('.retrieval-results')).toHaveCount(0)
    release()
    await page.getByLabel('待检索问题').fill(`${title}预约提前多久申请？`)
    await page.getByRole('button', { name: '开始检索' }).click()
    await expect(card.first()).toContainText('提前两天')
    await expect(page.locator('.retrieval-summary')).toContainText('BM25')
    await page.setViewportSize({ width: 390, height: 844 })
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    ).toBeTruthy()
  } finally {
    await admin.request.delete(`/api/documents/${doc.id}`, { headers: admin.headers })
    await admin.context.close()
  }
})
