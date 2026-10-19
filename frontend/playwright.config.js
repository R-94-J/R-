import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  timeout: 150_000,
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173',
    browserName: 'chromium',
    channel: process.env.E2E_BROWSER_CHANNEL || 'msedge',
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
