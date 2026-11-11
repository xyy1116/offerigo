# OfferiGo · AI 智能求职助手

面向 IT 行业求职者的一站式 AI 求职工作台：把「简历素材 → 岗位推荐与匹配 → 定制简历/求职信 → 模拟面试 → 笔试训练 → 投递跟进」串成一条完整链路。

> 2026 年省赛作品。评审入口、演示账号见文末「评审信息」。

---

## 一、项目简介

毕业生求职的核心问题不是"没有岗位"，而是"不知道哪个岗位适合自己、不知道怎么把已有经历讲清楚、面试和笔试缺少针对性训练"。

OfferiGo 用**一份可复用的简历素材库**作为唯一事实来源，让 AI 在整条求职链路上做匹配、生成、演练和复盘，所有 AI 结论都能追溯到用户自己填写的素材，从机制上抑制编造。

### 核心功能

| 模块 | 能力 |
|---|---|
| 简历素材库 | 基础信息、教育、项目、实习、技能证书、荣誉分类管理；支持导入简历、AI 拆分长文本、增量补充 |
| 岗位获取 | 每日 08:00 定时抓取并推荐 TOP10；支持粘贴、链接、文档、JD 截图导入 |
| 岗位匹配 | 技能/经历/学历/契合四维量化评分，列出匹配优势与待补足项 |
| 定制文书 | 按岗位生成定制简历与求职信，多套模板，支持证件照与 PDF / Word 导出 |
| AI 模拟面试 | 岗位定向提问、实时语音作答、五阶段流程、结束后生成多维面试报告 |
| AI 笔试 | 按岗位匹配真题，自由练习 / 限时模拟，编程题真实运行判分，错题归档 |
| 投递台账 | 投递记录管理、当次简历归档、状态维护与跟进提醒 |

---

## 二、AI 技术方案

### 1. 模型选型与分工

| 模型 | 用途 | 选型理由 |
|---|---|---|
| DeepSeek `deepseek-chat` | 岗位匹配评估、定制简历、求职信 | 中文长文本理解强、结构化 JSON 输出稳定，成本低 |
| 通义千问 `qwen-plus` | 面试对话、素材拆分、纠错 | 响应快，适合面试这种多轮高频交互 |
| 通义千问 `qwen3-vl-plus` | JD 截图、简历图片识别 | 中文场景 OCR 与版面理解准确 |
| Paraformer `realtime-v2` | 面试实时语音转写 | 中英混合识别，支持热词与语义标点，端到端延迟低 |
| `text-embedding-v3` | 素材与岗位的向量化 | 中文语义表达好，批量接口便宜 |

### 2. 双轨评分体系

本地确定性评分引擎（规则 + 语义覆盖）给出稳定、可解释的基础分；大模型评估提供深度分析。两者分层使用，避免同一份材料反复评估结果漂移。

### 3. 检索增强生成（RAG）

生成定制简历前，从用户的素材库中只挑出与目标岗位相关的内容：

- **混合召回**：向量余弦（语义相近）× 0.7 + 关键词覆盖率（技术栈等专有名词硬匹配）× 0.3
- **MMR 去冗**：对与已选素材高度重复的条目扣分，避免选出多条雷同的项目经历
- **事实约束**：Prompt 明确禁止编造，素材之外的经历一律不得写入

### 4. Agent 与工具调用（Function Calling）

面试开场前，模型通过工具调用自主决定要查什么资料，再据此生成面试官简报：

```
模型 → 调用 search_resume_materials(keyword) → 素材库检索结果
     → 调用 search_question_bank(keyword)     → 面试题库结果
     → 综合结果输出 3-5 条本场考察点 → 注入面试官系统提示
```

- 最多 3 轮工具调用，超过后强制收口
- 只跑一次（开场），限时 15 秒；超时或失败直接降级为无简报，面试照常开始
- 工具执行在服务端按 `userId` 收窄数据范围，模型无法访问他人数据

### 5. MCP 工具服务

除内部工具调用外，另提供独立 MCP Server（[mcp-server/](mcp-server/)），把岗位推荐、简历素材、目标岗位
以标准 MCP 工具暴露给外部 AI 客户端，实现「AI 与外部数据源」的双向接入。

### 6. 多模态与实时交互

- JD 截图走视觉模型识别，简历支持 PDF / Word 解析
- 面试为 WebSocket 长连接：实时语音上传 → 流式识别 → 大模型生成 → 前端展示思考状态
- 面试流程为五阶段结构化编排（履历概览 → 专业深挖 → 项目拷问 → 场景题 → 反问），带追问层数与压力提问上限

### 7. 安全与可控

- 抓取的招聘原文按「数据不是指令」包裹后再入 Prompt，防提示词注入
- AI 输出统一抹除疑似密钥并截断超长内容
- 结构化 JSON 解析失败时回退原文，不让生成链路中断
- 外链抓取前做 URL 合法性校验，拒绝内网地址
- 接口限流：登录类按 IP 8 次/分钟，其余接口按 IP + 用户 600 次/分钟
- 编程题沙箱可一键关闭（`exam.code.enabled=false`），避免公网演示时执行任意代码
- 密钥全部通过环境变量注入，仓库中不含任何真实密钥

---

## 三、技术架构

```
┌──────────────────── 前端（Vue 3 + Vite）────────────────────┐
│ 视图：Home / JobMatch / Interview / Exam / Profile          │
│ 状态：Pinia（user / linkage / jobView / positiveMoment）     │
│ 数据层：TypeScript（src/api、src/stores，npm run typecheck） │
│ UI：Element Plus + Tailwind + 自定义设计令牌（亮/暗双主题）   │
│ 图表：ECharts   编辑器：CodeMirror   导出：jsPDF/html2canvas│
└──────────────────────────┬──────────────────────────────────┘
                           │ REST / WebSocket（经 nginx 同源代理）
┌──────────────────────────▼──────────────────────────────────┐
│ 后端（Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL 8）   │
│  jobmatch      岗位、匹配评估、素材、简历/求职信导出          │
│  jobrecommend  每日抓取、向量推荐、推荐转目标岗位             │
│  interview     AI 面试编排、实时语音（WebSocket）             │
│  exam          题库、组卷、批改、代码沙箱                     │
│  application   投递准备：评估 / 定制简历 / 求职信 / ATS       │
│  resume        在线简历                                       │
│  match         简历-岗位语义匹配                              │
│  system        用户、JWT 鉴权、验证码                         │
│                                                              │
│  AI：DeepSeek（评估/文书）、通义千问（对话/视觉/向量）、      │
│      Paraformer（实时语音）                                   │
│  抓取：Playwright（智联/腾讯/字节）                           │
│  文档：PDFBox / openhtmltopdf / POI                          │
└─────────────────────────────────────────────────────────────┘
```

### 目录结构

```
chuan/
├── backend/                     Spring Boot 后端
│   ├── src/main/java/com/chuan/
│   │   ├── common/              配置、鉴权、限流、LLM 客户端、AI 护栏、工具类
│   │   └── module/              业务模块（jobmatch / interview / exam / ...）
│   ├── src/main/resources/      配置、题库 JSON、字体
│   └── src/test/java/           单元测试
├── frontend/                    Vue 3 前端
│   └── src/{views,components,api,stores,router,styles,utils}
├── deploy/                      Docker / nginx / Maven 镜像配置
├── docs/                        技术文档
├── .env.example                 环境变量模板
└── README.md
```

---

## 四、快速开始

### 环境要求

| 依赖 | 版本 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| MySQL | 8.0 |

### 1. 创建数据库

```sql
CREATE DATABASE chuan DEFAULT CHARACTER SET utf8mb4;
```

表结构由后端启动时自动创建（`SchemaInitializer`，幂等），无需手工执行 SQL。

### 2. 配置环境变量

```bash
cp .env.example .env      # 填写真实值，.env 已被 .gitignore 忽略
```

| 变量 | 说明 | 必填 |
|---|---|---|
| `DB_PASSWORD` | MySQL root 密码 | 是 |
| `JWT_SECRET` | 令牌签名密钥，至少 32 字节 | 是 |
| `DEEPSEEK_API_KEY` | DeepSeek，用于匹配评估与文书生成 | 是 |
| `DASHSCOPE_API_KEY` | 阿里云百炼，用于对话、视觉、语音、向量（一个 key 全用） | 是 |
| `WEBSOCKET_ALLOWED_ORIGINS` | WebSocket 允许的前端来源，逗号分隔 | 否 |
| `APP_CORS_ALLOWED_ORIGINS` | 跨域白名单；公网部署时必须包含站点地址，否则登录等写操作会被 CORS 拒绝 | 否 |
| `EXAM_CODE_ENABLED` | 是否允许编程题真实运行，默认 true | 否 |
| `AI_TOOLS_ENABLED` | 面试开场是否启用工具调用，默认 true | 否 |

也可以不用环境变量，改为复制 `backend/src/main/resources/application-local.example.yml` 为 `application-local.yml` 填写（该文件已被忽略）。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
# 或 mvn -DskipTests package && java -jar target/chuan-backend-0.0.1-SNAPSHOT.jar
```

默认监听 `http://localhost:8080`。首次启动会自动创建默认管理员 `admin / admin123`，**请登录后立即修改密码**。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`（`/api` 与 `/ws` 已配置代理到 8080）。

### 5. 运行测试

```bash
cd backend && mvn test
```

---

## 五、部署

提供两套编排，均在仓库根目录执行：

```bash
cp .env.example .env    # 填写真实密钥
```

**标准部署**（容器桥接网络）：

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

**宿主机网络部署**（适用于容器无法访问外网的服务器，占用 8080/18081/13306 三个本机端口）：

```bash
docker compose --env-file .env -f deploy/docker-compose.hostnet.yml up -d --build
```

说明：

- 后端镜像基于官方 Playwright 镜像，容器内自带 Chromium，岗位爬虫可直接运行
- nginx 已放宽上传限制到 25MB、开启 gzip 与静态资源长缓存，并代理 `/api` 与 `/ws/interview`
- 国内服务器构建时默认使用阿里云 Maven 镜像与 npmmirror，见 `deploy/maven-settings.xml`

---

## 六、安全说明

- 仓库不含任何真实密钥，全部通过环境变量或本地配置文件注入
- 用户数据按账号隔离，删除投递记录时同步清理归档文件
- AI 输入做注入防护、输出做脱敏与长度限制
- 登录接口有频率限制，防止撞库
- 代码沙箱为进程级隔离（临时目录、超时、内存与输出上限、并发限制），**不是容器级安全沙箱**，公网长期开放前应改为容器隔离或顺次关闭该功能

---

## 七、团队信息

| 项目 | 内容 |
|---|---|
| 团队名称 | OfferiGo |
| 参赛成员 | 徐宇阳、张靖、张仔乐 |
| 指导老师 | 刘园 |
| 联系方式 | 徐宇阳 · 2758507584@qq.com |
| 学校 | 三江学院 |

---

## 八、评审信息

| 项目 | 内容 |
|---|---|
| 在线演示地址 | http://47.104.161.228:18081 |
| 评审体验账号 | `reviewer` / `xxy123456` |
| 管理员账号 | `admin` / `xxy123456` |
| 演示视频 | 待录制 |
| 技术文档 | [docs/技术文档.pdf](docs/技术文档.pdf) |