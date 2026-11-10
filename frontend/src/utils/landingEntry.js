let mode = null

// 'match' = 从其他页面进入岗位匹配；'home' = 站内回到首页；null = 首次打开
export function setLandingMode(value) {
  mode = value
}

export function consumeLandingMode() {
  const value = mode
  mode = null
  return value
}