import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '@/App.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import { createTestRouter } from './index'

describe('application router', () => {
  it('redirects the default route to the Dashboard placeholder', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('mounts the router through App', async () => {
    const router = createTestRouter()
    await router.push('/scores')
    await router.isReady()
    const wrapper = mount(App, { global: { plugins: [router], stubs: { AppShell: { template: '<router-view />' } } } })
    expect(wrapper.text()).toContain('成绩')
  })

  it('allows direct navigation without an auth guard', async () => {
    const router = createTestRouter()
    await router.push('/settings')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/settings')
  })

  it('uses one PlaceholderView for unfinished routes', () => {
    const router = createTestRouter()
    const components = router.getRoutes().filter((route) => route.path !== '/' && route.redirect == null).map((route) => route.components?.default)
    expect(components.every((component) => component === PlaceholderView)).toBe(true)
  })
})
