import { computed, ref } from 'vue'

export type ThemeMode = 'system' | 'light' | 'dark'
type ResolvedTheme = 'light' | 'dark'

const STORAGE_KEY = 'theme-mode'
const THEME_MODES: ThemeMode[] = ['system', 'light', 'dark']

const media: MediaQueryList | null =
  typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)')
    : null

const systemDark = ref<boolean>(media ? media.matches : false)

function readMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved && (THEME_MODES as string[]).includes(saved)) return saved as ThemeMode
  } catch (e) {
    // 隐私模式下 localStorage 可能不可用
  }
  return 'system'
}

export const themeMode = ref<ThemeMode>(readMode())

export const resolvedTheme = computed<ResolvedTheme>(() => {
  if (themeMode.value === 'system') return systemDark.value ? 'dark' : 'light'
  return themeMode.value
})

function paint(): void {
  const dark = resolvedTheme.value === 'dark'
  const root = document.documentElement
  root.dataset.theme = dark ? 'dark' : 'light'
  // Element Plus 暗色变量挂在 html.dark 上
  root.classList.toggle('dark', dark)
  root.style.colorScheme = dark ? 'dark' : 'light'
  root.style.backgroundColor = dark ? '#0d0d0d' : '#f4f7fa'
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', dark ? '#0d0d0d' : '#ffffff')
}

export function setThemeMode(next: ThemeMode): void {
  if (!THEME_MODES.includes(next)) return
  themeMode.value = next
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch (e) {
    // 忽略存储失败
  }
  paint()
}

export function initTheme(): void {
  if (media) systemDark.value = media.matches
  paint()
  if (!media) return
  const onChange = (event: MediaQueryListEvent): void => {
    systemDark.value = event.matches
    if (themeMode.value === 'system') paint()
  }
  if (media.addEventListener) media.addEventListener('change', onChange)
  else media.addListener(onChange)
}