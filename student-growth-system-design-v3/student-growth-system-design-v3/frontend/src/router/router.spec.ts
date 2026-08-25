import { describe, expect, it } from 'vitest'

import PlaceholderView from '@/views/PlaceholderView.vue'
import ScoreView from '@/views/ScoreView.vue'
import WrongQuestionView from '@/views/WrongQuestionView.vue'
import { createTestRouter } from './index'

describe('application router', () => {
  it('redirects the default route to the real Dashboard', async () => {
    const router = createTestRouter()
    await router.push('/')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('mounts the real score page instead of a placeholder', async () => {
    const router = createTestRouter()
    await router.push('/scores')
    await router.isReady()
    expect(router.currentRoute.value.matched.at(-1)?.components?.default).toBe(ScoreView)
  })

  it('mounts the real wrong question page instead of a placeholder', async () => {
    const router = createTestRouter()
    await router.push('/wrong-questions')
    await router.isReady()
    expect(router.currentRoute.value.matched.at(-1)?.components?.default).toBe(WrongQuestionView)
  })

  it('allows direct navigation without an auth guard', async () => {
    const router = createTestRouter()
    await router.push('/settings')
    await router.isReady()
    expect(router.currentRoute.value.path).toBe('/settings')
  })

  it('uses one PlaceholderView for unfinished routes', () => {
    const router = createTestRouter()
    const components = router
      .getRoutes()
      .filter((route) => !['/', '/dashboard', '/students', '/scores', '/wrong-questions'].includes(route.path) && route.redirect == null)
      .map((route) => route.components?.default)
    expect(components.every((component) => component === PlaceholderView)).toBe(true)
  })
})
