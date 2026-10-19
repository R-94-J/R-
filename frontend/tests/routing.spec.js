import { test, expect } from '@playwright/test'

test('official website sources and non-official academic label are visible; unrelated follow-up is rejected', async ({
  page,
}) => {
  test.setTimeout(210000)
  await page.goto('/student')
  await expect(page.getByLabel('输入校园问题')).toBeEnabled()
  async function ask(question) {
    await page.getByLabel('输入校园问题').fill(question)
    await page.getByLabel('发送问题').click()
    await expect(page.getByLabel('输入校园问题')).toBeEnabled({ timeout: 170000 })
    return page.locator('.message.assistant').last()
  }
  const official = await ask('山东青年政治学院的校训是什么？')
  await expect(official.locator('.mode-label')).toHaveText('官网检索回答')
  await expect(official).toContainText('自强不息')
  await expect(official.getByRole('link', { name: /学校简介 · 查看官网原文/ })).toHaveAttribute(
    'href',
    'https://www.sdyu.edu.cn/xxgk/xxjj.htm',
  )
  await expect(official.getByRole('button', { name: /查看完整资料/ })).toHaveCount(0)
  await page.getByRole('button', { name: /开启新对话/ }).click()
  const academic = await ask('请解释Java的快速失败迭代器和ConcurrentModificationException的原理。')
  await expect(academic.locator('.mode-label')).toHaveText('非官方参考')
  await expect(academic.locator('.message-content')).toContainText('以下非学校官方资料')
  await expect(academic.locator('.source-card')).toHaveCount(0)
  const refused = await ask('那再给我讲一个明星八卦。')
  await expect(refused.locator('.mode-label')).toHaveText('超出服务范围')
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  for (const conversation of await (await page.request.get('/api/conversations')).json())
    await page.request.delete(`/api/conversations/${conversation.id}`, {
      headers: { [csrf.headerName]: csrf.token },
    })
})
