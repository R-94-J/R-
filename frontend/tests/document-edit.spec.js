import { test, expect } from '@playwright/test'

test('document metadata can be maintained while original files and historical citations are preserved', async ({
  page,
  context,
}) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  let documentId
  const conversationIds = new Set()
  const title = `图书馆维护测试 ${Date.now()}`
  const updatedTitle = `${title} 修订版`
  const bytes = Buffer.from(
    `# ${title}\n图书馆每天08:00开门，22:00关门。本科生可以借阅10册图书，借期30天。`,
  )
  try {
    await page.goto('/admin')
    const login = page.getByRole('dialog', { name: '管理员登录' })
    await login.getByLabel('用户名').fill(process.env.E2E_ADMIN_USERNAME || 'admin')
    await login.getByLabel('密码').fill(process.env.E2E_ADMIN_PASSWORD || 'Campus@2026')
    await login.getByRole('button', { name: '登录', exact: true }).click()
    await expect(login).not.toBeVisible()
    await page.getByRole('button', { name: '上传资料', exact: true }).click()
    const upload = page.getByRole('dialog', { name: '添加校园资料' })
    await upload
      .locator('input[type=file]')
      .setInputFiles({ name: 'library.md', mimeType: 'text/markdown', buffer: bytes })
    await upload.getByLabel('文档标题').fill(title)
    await upload.getByLabel('资料分类').selectOption('学籍与学位')
    await upload.getByLabel('发布单位').fill('测试图书馆')
    await upload.getByLabel('官网发布日期').fill('2025-03-01')
    await upload.getByLabel('适用说明').fill('适用于全体在校生')
    await upload.getByRole('button', { name: '上传并加入知识库' }).click()
    await expect(upload).not.toBeVisible({ timeout: 30000 })
    const docs = await (await page.request.get('/api/documents')).json()
    documentId = docs.find((doc) => doc.title === title).id
    const original = await (await page.request.get(`/api/documents/${documentId}`)).json()
    expect(original.document.publisher).toBe('测试图书馆')
    expect(original.document.publishedDate).toBe('2025-03-01')
    await page.getByLabel('搜索文档').fill(title)
    let row = page.getByRole('row').filter({ hasText: title })
    await expect(row).toContainText('待补来源')
    await row.getByRole('button', { name: '查看', exact: true }).click()
    await expect(page.getByRole('dialog', { name: title })).toContainText('适用于全体在校生')
    await page.getByRole('dialog', { name: title }).getByLabel('关闭弹窗').click()

    await page.getByRole('button', { name: /开启新对话/ }).click()
    await page.getByLabel('输入校园问题').fill(`${title}规定图书馆几点关门？`)
    const beforeResponse = page.waitForResponse(
      (response) => response.url().endsWith('/api/chat') && response.request().method() === 'POST',
    )
    await page.getByLabel('发送问题').click()
    const before = await (await beforeResponse).json()
    conversationIds.add(before.conversationId)
    await expect(page.locator('.message.assistant')).toContainText('22:00', { timeout: 60000 })
    await expect(page.locator('.thinking')).toHaveCount(0)

    await page
      .getByRole('navigation')
      .getByRole('button', { name: /知识库/ })
      .click()
    await row.getByRole('button', { name: '编辑', exact: true }).click()
    const edit = page.getByRole('dialog', { name: '编辑资料信息' })
    await expect(edit.getByLabel('文档标题')).toHaveValue(title)
    await expect(edit.getByLabel('发布单位')).toHaveValue('测试图书馆')
    await edit.getByLabel('文档标题').fill(updatedTitle)
    await edit.getByLabel('资料分类').selectOption('学习资源')
    await edit.getByLabel('官网来源').fill('https://lib.sdyu.edu.cn/info/1001/1.htm')
    await edit.getByLabel('发布单位').fill('更新后的图书馆')
    await edit.getByLabel('官网发布日期').fill('2026-03-01')
    await edit.getByLabel('适用说明').fill('自2026级学生开始执行')
    await edit.getByLabel('原文件下载链接').fill('https://lib.sdyu.edu.cn/files/guide.docx')
    await edit.getByRole('button', { name: '保存资料信息' }).click()
    await expect(edit).not.toBeVisible()
    row = page.getByRole('row').filter({ hasText: updatedTitle })
    await expect(row).toContainText('学习资源')
    await expect(row).not.toContainText('待补来源')
    const updated = await (await page.request.get(`/api/documents/${documentId}`)).json()
    expect(updated.chunks).toEqual(original.chunks)
    expect(updated.document.contentHash).toBe(original.document.contentHash)
    const file = await page.request.get(`/api/files/${documentId}`)
    expect(await file.body()).toEqual(bytes)

    await page.reload()
    await page.locator('.conversation-link').filter({ hasText: title }).click()
    await expect(page.locator('.message.assistant .source-card').first()).toContainText(title)
    await expect(page.locator('.message.assistant .source-card').first()).not.toContainText(
      '修订版',
    )
    await expect(page.locator('.message.assistant')).toContainText('适用于全体在校生')
    await page.getByRole('button', { name: /开启新对话/ }).click()
    await page.getByLabel('输入校园问题').fill(`${updatedTitle}规定图书馆几点关门？`)
    const afterResponse = page.waitForResponse(
      (response) => response.url().endsWith('/api/chat') && response.request().method() === 'POST',
    )
    await page.getByLabel('发送问题').click()
    const after = await (await afterResponse).json()
    conversationIds.add(after.conversationId)
    await expect(page.locator('.message.assistant')).toContainText('自2026级学生开始执行', {
      timeout: 60000,
    })
    await expect(
      page
        .locator('.message.assistant')
        .getByRole('link', { name: `${updatedTitle} · 查看官网原文 ↗` }),
    ).toHaveAttribute('href', 'https://lib.sdyu.edu.cn/info/1001/1.htm')
    await expect(page.locator('.thinking')).toHaveCount(0)

    await page
      .getByRole('navigation')
      .getByRole('button', { name: /知识库/ })
      .click()
    await page.getByLabel('搜索文档').fill(updatedTitle)
    await page
      .getByRole('row')
      .filter({ hasText: updatedTitle })
      .getByRole('button', { name: '查看', exact: true })
      .click()
    await page
      .getByRole('dialog', { name: updatedTitle })
      .getByRole('button', { name: '编辑资料信息' })
      .click()
    await page.setViewportSize({ width: 390, height: 844 })
    await expect(edit.getByLabel('发布单位')).toHaveValue('更新后的图书馆')
    expect(
      await edit.evaluate((element) => element.scrollWidth <= element.clientWidth),
    ).toBeTruthy()
    await edit.getByLabel('原文件下载链接').fill('')
    await edit.getByRole('button', { name: '保存资料信息' }).click()
    await expect(edit).not.toBeVisible()
    const restored = await (await page.request.get(`/api/documents/${documentId}`)).json()
    expect(restored.document.downloadUrl).toBe(`/api/files/${documentId}`)

    const guest = await context.browser().newContext({ baseURL: test.info().project.use.baseURL })
    try {
      const csrf = await (await guest.request.get('/api/auth/csrf')).json()
      const denied = await guest.request.patch(`/api/documents/${documentId}`, {
        headers: { [csrf.headerName]: csrf.token },
        data: { title: '访客尝试修改' },
      })
      expect(denied.status()).toBe(401)
    } finally {
      await guest.close()
    }
    expect(errors).toEqual([])
  } finally {
    const csrf = await (await page.request.get('/api/auth/csrf')).json()
    const headers = { [csrf.headerName]: csrf.token }
    for (const id of conversationIds)
      await page.request.delete(`/api/conversations/${id}`, { headers })
    if (documentId) await page.request.delete(`/api/documents/${documentId}`, { headers })
  }
})
