const TOKEN_KEY = 'chuan_token'

/** 读取本地令牌；不存在时返回空串，调用方可直接用真假判断 */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}