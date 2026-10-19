import { test, expect } from '@playwright/test'

test('student registration, database login, persistent history and official Word link', async ({
  page,
  browser,
}) => {
  test.setTimeout(120000)
  const username = `e2e_student_${Date.now()}`
  const password = 'TestStudent!2026'
  await page.goto('/student')
  await expect(page.getByLabel('输入校园问题')).toBeEnabled()
  await page.getByRole('button', { name: /学生登录 \/ 注册/ }).click()
  await page.getByRole('button', { name: '没有账号？立即注册' }).click()
  const signup = page.getByRole('dialog', { name: '学生注册' })
  await signup.getByLabel('用户名', { exact: true }).fill(username)
  await signup.getByLabel('密码', { exact: true }).fill(password)
  await signup.getByLabel('确认密码', { exact: true }).fill(password)
  await signup.getByRole('button', { name: '注册并登录' }).click()
  await expect(signup).not.toBeVisible()
  await expect(page.locator('.profile')).toContainText(username)
  expect((await (await page.request.get('/api/auth/me')).json()).admin).toBe(false)
  expect((await page.request.get('/api/documents')).status()).toBe(403)
  const question = '课程重修免听申请表Word在哪里下载？'
  await page.getByLabel('输入校园问题').fill(question)
  await page.getByLabel('发送问题').click()
  const link = page
    .locator('.message.assistant')
    .getByRole('link', { name: '下载文件：山东青年政治学院课程重修免听申请表 ↓' })
  await expect(link).toHaveAttribute('href', /wbfileid=16787504/, { timeout: 60000 })
  await expect(page.getByLabel('输入校园问题')).toBeEnabled()
  await page.getByRole('button', { name: new RegExp(username + '.*点击退出') }).click()
  await expect(page.locator('.conversation-link')).toHaveCount(0)

  const context = await browser.newContext()
  const other = await context.newPage()
  await other.goto('/student')
  await expect(other.getByLabel('输入校园问题')).toBeEnabled()
  await other.getByRole('button', { name: /学生登录 \/ 注册/ }).click()
  const login = other.getByRole('dialog', { name: '学生登录' })
  await login.getByLabel('用户名').fill(username)
  await login.getByLabel('密码', { exact: true }).fill(password)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login).not.toBeVisible()
  await other.locator('.conversation-link').filter({ hasText: question }).click()
  await expect(
    other
      .locator('.message.assistant')
      .getByRole('link', { name: /下载文件：山东青年政治学院课程重修免听申请表/ }),
  ).toHaveAttribute('href', /wbfileid=16787504/)
  const csrf = await (await other.request.get('/api/auth/csrf')).json()
  const histories = await (await other.request.get('/api/conversations')).json()
  for (const conversation of histories)
    await other.request.delete(`/api/conversations/${conversation.id}`, {
      headers: { [csrf.headerName]: csrf.token },
    })
  await context.close()
})
