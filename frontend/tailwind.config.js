/** @type {import('tailwindcss').Config} */
export default {
  // 扫描整个 src：新增页面/组件无需再改配置，避免开发服务器漏生成工具类
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {}
  },
  corePlugins: {
    // 关闭 preflight，避免重置 Element Plus 与项目既有全局样式
    preflight: false
  }
}
