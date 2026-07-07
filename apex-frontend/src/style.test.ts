import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

describe('global style tokens', () => {
  const stylePath = resolve(dirname(fileURLToPath(import.meta.url)), 'style.css')
  const css = readFileSync(stylePath, 'utf8')
  const workspacePagePath = resolve(
    dirname(fileURLToPath(import.meta.url)),
    'features/workspace/pages/WorkspacePage.vue',
  )
  const workspacePageSource = readFileSync(workspacePagePath, 'utf8')
  const chatPanePath = resolve(
    dirname(fileURLToPath(import.meta.url)),
    'features/workspace/components/ChatPane.vue',
  )
  const chatPaneSource = readFileSync(chatPanePath, 'utf8')

  it('uses a near-white workspace palette and restrained radii', () => {
    expect(css).toContain('--page: #f7f7f8;')
    expect(css).toContain('--surface: #ffffff;')
    expect(css).toContain('--surface-muted: #f3f4f6;')
    expect(css).toContain('--surface-subtle: #f8f8fb;')
    expect(css).toContain('--radius-panel: 16px;')
    expect(css).toContain('--radius-control: 10px;')
  })

  it('keeps buttons compact and shadows light', () => {
    expect(css).toContain('min-height: 36px;')
    expect(css).toContain('--shadow-panel: 0 16px 40px -32px rgba(15, 23, 42, 0.18);')
  })

  it('keeps the workspace main column stretched to the viewport height', () => {
    expect(workspacePageSource).toContain('.workspace-page__main {')
    expect(workspacePageSource).toContain('display: flex;')
    expect(workspacePageSource).toContain('min-height: 100dvh;')
    expect(workspacePageSource).toContain('.workspace-page__main-shell {')
    expect(workspacePageSource).toContain('height: 100%;')
    expect(workspacePageSource).toContain('.workspace-page__main-column {')
    expect(workspacePageSource).toContain('flex: 1;')
  })

  it('keeps the chat pane full-width before and after the first reply', () => {
    expect(chatPaneSource).toContain('.chat-pane {')
    expect(chatPaneSource).toContain('width: 100%;')
    expect(chatPaneSource).toContain('.chat-pane__shell {')
    expect(chatPaneSource).toContain('max-width: 920px;')
  })
})
