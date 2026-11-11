let mode: string | null = null

/** 'match' = 从其他页面进入岗位匹配；'home' = 站内回到首页；null = 首次打开 */
export function setLandingMode(value: string): void {
  mode = value
}

export function consumeLandingMode(): string | null {
  const value = mode
  mode = null
  return value
}