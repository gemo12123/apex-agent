import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

describe('global style tokens', () => {
  const stylePath = resolve(dirname(fileURLToPath(import.meta.url)), 'style.css')
  const css = readFileSync(stylePath, 'utf8')

  it('uses a near-white workspace palette and restrained radii', () => {
    expect(css).toContain('--page: #f7f7f8;')
    expect(css).toContain('--surface: #ffffff;')
    expect(css).toContain('--surface-muted: #f3f4f6;')
    expect(css).toContain('--radius-panel: 18px;')
    expect(css).toContain('--radius-control: 12px;')
  })

  it('keeps buttons compact and shadows light', () => {
    expect(css).toContain('min-height: 38px;')
    expect(css).toContain('--shadow-panel: 0 16px 40px -32px rgba(15, 23, 42, 0.18);')
  })
})
