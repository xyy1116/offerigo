# OfferiGo · AI 智能求职助手

面向 IT 行业求职者的一站式 AI 求职工作台：把「简历素材 → 岗位推荐与匹配 → 定制简历/求职信 → 模拟面试 → 笔试训练 → 投递跟进」串成一条完整链路。

> 省赛作品 · 演示账号与在线地址见文末「评审信息」。

---

## 一、项目简介

毕业生求职的核心问题不是"没有岗位"，而是"不知道哪个岗位适合自己、不知道怎么把已有经历讲清楚、面试和笔试没有针对性训练"。OfferiGo 用一份可复用的简历素材库作为事实来源，让 AI 在整条求职链路上做匹配、生成、演练和复盘，所有结论都能追溯到用户自己的素材。

### 核心功能

| 模块 | 能力 |
|---|---|
| 简历素材库 | 基础信息、教育、项目、实习、技能证书、荣誉分类管理；支持导入简历、AI 拆分长文本、增量补充 |
| 岗位获取 | 每日 08:00 自动抓取并推荐 TOP10；支持粘贴、链接、文档、JD 截图导入 |
| 岗位匹配 | 技能/经历/学历/契合四维量化评分，列出匹配优势与待补足项；90 分以上高亮 |
| 定制文书 | 按岗位生成定制简历与求职信，内置多套模板，支持证件照与 PDF / Word 导出 |
| AI 模拟面试 | 岗位定向提问、实时语音作答、结束后生成多维面试报告，历史可复盘 |
| AI 笔试助手 | 按岗位匹配真题，自由练习 / 限时模拟，错题归档、重做、掌握标记 |
| 投递台账 | 投递记录管理、当次简历归档、状态维护与跟进提醒 |

---

## 二、AI 技术亮点

- **双轨评分体系**：本地确定性评分引擎（规则 + 语义覆盖）负责稳定、可解释的基础分；后端大模型评估提供深度分析。两者分层使用，避免同一份材料反复评估结果漂移。
- **基于个人素材的检索增强生成（RAG）**：使用向量检索（Embedding + 余弦相似度）从简历素材中选出与目标岗位最相关的条目，再交给大模型生成定制简历与求职信，降低无关内容与编造风险。
- **多模态输入**：岗位 JD 截图通过视觉大模型识别，面试回答通过实时语音识别（Paraformer）转写，证件照/头像支持本地裁剪压缩。
- **阶段化 AI 工作流**：简历生成、求职信、匹配评估、试卷生成等任务均有真实阶段进度（准备 → 生成 → 解析），前端可视化，不是假进度条。
- **可控与兜底**：Prompt 明确禁止编造经历；结构化 JSON 解析失败时回退原文；外链抓取有 URL 白名单与内网地址校验；密钥全部通过环境变量注入。

---

## 三、技术架构

```
┌───────────────────────── 前端（Vue 3 + Vite）─────────────────────────┐
│  视图层：Home / JobMatch / Interview / Exam / Profile                  │
│  状态层：Pinia（用户、联动、正向反馈）                                  │
│  匹配引擎：src/utils/jobMatchEngine.js（确定性评分 + 素材覆盖判定）      │
│  UI：Element Plus + 自定义主题令牌（亮/暗双主题） + ECharts             │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ REST / WebSocket
┌───────────────────────────────▼───────────────────────────────────────┐
│ 后端（Spring Boot 3 + MyBatis-Plus + MySQL 8）                         │
│  jobmatch      岗位、匹配评估、素材、简历/求职信导出                    │
│  jobrecommend  每日爬虫、向量推荐、推荐转目标岗位                       │
│  interview     AI 面试编排、实时语音（WebSocket）                       │
│  exam          题库爬取、组卷、批改、错题                               │
│  application   投递准备：评估 / 定制简历 / 求职信 / ATS                 │
│  system        用户、鉴权（JWT）、头像                                  │
│                                                                         │
│  AI 能力：DeepSeek（评估/文书）、通义千问（对话/视觉/Embedding）、       │
│           Paraformer（实时语音识别）                                   │
│  抓取能力：Playwright（智联/腾讯/字节 岗位与 JD）                       │
│  文档能力：PDFBox / openhtmltopdf / POI（PDF / Word 导出）              │
└───────────────────────────────┬───────────────────────────────────────┘
                                │
                        MySQL 8（业务数据）
```

### 目录结构

```
chuan/
├── backend/                  Spring Boot 服务
│   ├── src/main/java/com/chuan/
│   │   ├── common/           配置、鉴权、LLM 客户端、工具类
│   │   └── module/           业务模块（jobmatch / interview / exam ...）
│   └── src/test/java/        单元测试
├── frontend/                 Vue 3 前端
│   ├── src/views/            页面
│   ├── src/components/       业务组件
│   ├── src/utils/            匹配引擎、音频、主题等
│   └── src/styles/           设计令牌与全局样式（亮/暗主题）
└── docs/                     技术文档、部署指南、演示脚本
```

---

## 四、快速开始

### 环境要求

- JDK 17+（推荐 21）
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- 首次运行抓取功能需要 Playwright Chromium

### 1. 初始化数据库

```sql
CREATE DATABASE chuan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

表结构由后端启动时自动创建（`SchemaInitializer`），无需手动导入。

### 2. 配置环境变量

复制模板并填写：

```bash
cp backend/src/main/resources/application-local.example.yml backend/src/main/resources/application-local.yml
cp .env.example .env
```

必填项：

| 变量 | 说明 |
|---|---|
| `DB_PASSWORD` | MySQL 密码 |
| `JWT_SECRET` | JWT 签名密钥（生产环境必须更换） |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（评估、文书生成） |
| `DASHSCOPE_API_KEY` | 阿里云百炼 Key（对话、视觉、Embedding、语音识别） |

可选项：

| 变量 | 说明 |
|---|---|
| `GITHUB_TOKEN` | 题库爬虫访问 GitHub API 的令牌（提升限额） |
| `WEBSOCKET_ALLOWED_ORIGINS` | 允许的 WebSocket 来源，逗号分隔 |
| `exam-bank.cron` / `job-crawl.cron` | 定时抓取表达式，默认每日 08:00 |

> 任何 API Key 都不要写进会被提交的文件；`application-local.yml` 与 `.env` 已在 `.gitignore` 中忽略。

### 3. 安装 Playwright 浏览器（仅抓取功能需要）

```bash
cd backend
./install-playwright.cmd   # Windows；其他系统执行对应的 playwright install 命令
```

### 4. 启动

```bash
# 后端（默认 8080）
cd backend
mvn spring-boot:run

# 前端（默认 5173，/api 代理到 8080）
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

首次启动会自动创建默认管理员账号，**请在首次登录后自行修改密码**（默认账号见启动日志/初始化配置）。

### 5. 构建

```bash
cd frontend && npm run build     # 产物在 frontend/dist
cd backend && mvn -DskipTests package
```

部署方式见 [docs/部署指南.md](docs/部署指南.md)。

---

## 五、测试

```bash
cd backend && mvn test
```

当前覆盖：进度存储、URL 安全校验、向量计算、封面信导出、代码沙箱等核心工具与流程。

---

## 六、安全说明

- 所有密钥通过环境变量/本地配置文件注入，仓库中不包含真实密钥。
- 外链抓取前进行 URL 合法性校验，拒绝内网地址。
- AI 生成内容禁止编造候选人经历；解析异常有原文回退。
- 用户数据按账号隔离，删除投递记录时同步清理归档简历文件。

---

## 七、团队信息

- 团队名称：待填写
- 成员：待填写
- 指导老师：待填写
- 联系方式：待填写

---

## 八、评审信息

- 在线演示地址：待部署后填写
- 评审账号：待填写（建议单独创建只读体验账号）
- 演示视频：待录制（脚本见 [docs/演示视频脚本.md](docs/演示视频脚本.md)）
- 技术文档：[docs/技术文档.md](docs/技术文档.md)