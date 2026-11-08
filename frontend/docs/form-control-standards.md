# 输入组件使用规范（Form Control Standards）

> 适用：`frontend`（Vue3 + Element Plus 2.7.5）。所有新增/修改的输入组件必须遵循本文档，保持全站一致。

## 1. 组件选型

| 场景 | 组件 | 说明 |
|------|------|------|
| 单行文本 | `<el-input>` | 默认盒式，统一高度 36px |
| 多行文本 | `<el-input type="textarea">` | 代码题可加 `.code-input` 类设等宽字体 |
| 下拉选择 | `<el-select>` | `clearable` 按需开启 |
| 日期/时间 | `<el-date-picker>` | 统一 `value-format` |
| 数字 | `<el-input-number>` | 指定 min/max/step |
| 级联选择 | `<el-cascader>` | 地区级联等 |
| 单选/多选 | `<el-radio-group>` / `<el-checkbox-group>` | 选项文字用 `stripLetter` 去除前缀 |
| 开关 | `<el-switch>` | — |

不使用自定义输入组件（已废弃下划线浮动标签 `WaveInput`）。仅登录/注册页允许使用原生 `<input>`（见 §6 例外）。

## 2. 视觉规范（由 `src/styles/form.css` 全局统一）

- **尺寸**：统一高度 36px（`--el-component-size`）；`size="large"` 仅用于认证卡片。
- **边框**：默认 `1px` `var(--color-border)`；圆角 `var(--radius-sm)`（4px）。
- **聚焦**：边框变主色 `var(--color-primary)`。
- **悬浮**：边框变浅紫 `var(--color-primary-light)`。
- **占位符**：`var(--color-text-2)`（浅灰），格式统一为「动词 + 内容」提示（如 `请输入…` / `请选择…` / `搜索…`）。
- **禁用/只读**：背景 `var(--color-bg)`、文字 `var(--color-text-2)`。

开发时不要在本组件 `≤style scoped≤` 内重复覆盖以上属性；布局性尺寸（如工具栏宽度）可用内联 `style`/类控制。

## 3. 校验规则

- 必填一律在 `rules` 中用 `required: true` 表达，**不手写 `*`**（红色必填星号由 Element Plus 自动渲染）。
- `trigger` 约定：文本框/密码等 `blur`；下拉/选择类 `change`。
- 复用共享校验器（`src/utils/validators.js`），不要在各视图重复定义：
  - `required(message[, trigger])`
  - `minMax(min, max, message)`
  - `phoneRule([message])`
  - `emailRule([message])`
  - `confirmPasswordRule(getPassword[, message])`

示例：

```js
import { required, minMax, emailRule, confirmPasswordRule } from '@/utils/validators'

const rules = {
  username: [required('请输入用户名'), minMax(3, 20, '用户名长度需在 3-20 之间')],
  email: [emailRule()],
  confirmPassword: [required('请再次输入密码'), confirmPasswordRule(() => form.password)]
}
```

## 4. 错误提示

- Element Plus 表单统一使用 `el-form-item` 内联错误（输入框下方，`var(--color-danger)`，字号 `var(--font-sm)`）。
- 不使用 `ElMessage` 做字段级校验错误；`ElMessage` 仅用于全局/流程性提示（如登录成功、提交结果）。

## 5. 必填标识与辅助文本

- 必填标识：红 `*`（自动），颜色 `var(--color-danger)`。
- 辅助文本：用 `placeholder` 承载填写说明；长度反馈用 `el-input` 的 `maxlength` + `show-word-limit`；避免额外散落的灰色提示文字。

## 6. 例外：登录/注册「认证卡片」

登录/注册页是唯一例外，采用专属「认证卡片」样式（2px 紫边框 `var(--color-primary)` + 悬停反色、白底圆角），由 `Login.vue` 的 scoped 样式维护。新页面**不得**复用此样式；登录/注册表单自身的 el-input 也遵循统一的必填/校验/错误约定。

## 7. 响应式

- 断点：`768px`（全局标准）。
- 移动端（≤768px）：`.el-input` / `.el-select` / `.el-date-editor` / `.el-input-number` / `.el-cascader` 自动满宽（由 `form.css` 处理）。
- 表单容器用 `el-row :gutter` 栅格换行，避免固定宽度造成横向溢出。