# OfferiGo MCP Server

把求职工作台的数据以 [MCP](https://modelcontextprotocol.io) 工具形式暴露给外部 AI 客户端（Claude Desktop、IDE 助手等），
模型无需自己实现数据接入，即可查询岗位推荐、检索简历素材、列出目标岗位。

## 提供的工具

| 工具 | 作用 | 依赖接口 |
|---|---|---|
| `get_daily_job_recommendations` | 当日推荐岗位（名称、公司、地点、薪资、匹配分） | `GET /api/job/recommend/today` |
| `search_resume_materials` | 按关键词检索简历素材库 | `GET /api/jobmatch/knowledge` |
| `search_target_jobs` | 按关键词检索目标岗位与岗位要求 | `GET /api/jobmatch/jobs` |

工具在服务端按传入的 JWT 归属用户收窄数据范围，模型无法读取他人数据。

## 运行

```bash
cd mcp-server
npm install
MCP_API_BASE=http://127.0.0.1:8080 MCP_API_TOKEN=<登录后拿到的 JWT> npm start
```

| 环境变量 | 说明 |
|---|---|
| `MCP_API_BASE` | 后端地址，默认 `http://127.0.0.1:8080` |
| `MCP_API_TOKEN` | 登录用户 JWT；为空时所有工具调用都会返回未认证 |

传输方式为 stdio，可直接在支持 MCP 的客户端里这样配置：

```json
{
  "mcpServers": {
    "offerigo": {
      "command": "node",
      "args": ["<仓库路径>/mcp-server/server.js"],
      "env": { "MCP_API_BASE": "http://127.0.0.1:8080", "MCP_API_TOKEN": "<JWT>" }
    }
  }
}
```