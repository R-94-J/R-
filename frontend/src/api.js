let csrf

export async function refreshCsrf() {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('无法建立会话，请检查后端服务是否启动')
  csrf = await response.json()
}

async function request(path, options = {}) {
  const method = options.method || 'GET'
  const headers = new Headers(options.headers)
  if (!['GET', 'HEAD'].includes(method)) {
    if (!csrf) await refreshCsrf()
    headers.set(csrf.headerName, csrf.token)
  }
  let body = options.body
  if (body && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }
  let response
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      method,
      headers,
      body,
      credentials: 'same-origin',
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error('网络连接失败，请检查服务后重试')
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}))
    if (response.status === 403) await refreshCsrf().catch(() => {})
    const error = new Error(data.message || `请求失败（${response.status}），请稍后重试`)
    error.status = response.status
    throw error
  }
  return response
}

export async function api(path, options = {}) {
  const response = await request(path, options)
  return response.status === 204 ? null : response.json()
}

export async function download(path, filename) {
  const response = await request(path)
  const url = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
