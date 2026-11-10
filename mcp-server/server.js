#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js'

/**
 * OfferiGo MCP Server
 *
 * 把求职工作台里的数据以 MCP 工具形式暴露给外部 AI 客户端（Claude Desktop、IDE 助手等），
 * 让模型可以直接查询岗位推荐、检索简历素材、列出目标岗位，而不必自己实现一遍数据接入。
 *
 * 运行：MCP_API_BASE=http://127.0.0.1:8080 MCP_API_TOKEN=<JWT> npm start
 * 说明：stdio 传输，日志一律走 stderr，避免污染协议输出。
 */

const API_BASE = process.env.MCP_API_BASE || 'http://127.0.0.1:8080'
const API_TOKEN = process.env.MCP_API_TOKEN || ''

async function apiGet(path) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: API_TOKEN ? { Authorization: `Bearer ${API_TOKEN}` } : {}
  })
  if (!response.ok) {
    throw new Error(`接口请求失败：HTTP ${response.status}`)
  }
  const body = await response.json()
  if (body.code !== 200) {
    throw new Error(body.message || '接口返回业务错误')
  }
  return body.data
}

/** 在列表里按关键词做轻量过滤，命中内容取前 limit 条 */
function pickByKeyword(list, keyword, limit) {
  const items = Array.isArray(list) ? list : []
  if (!keyword) return items.slice(0, limit)
  const needle = String(keyword).toLowerCase()
  return items
    .filter((item) => JSON.stringify(item).toLowerCase().includes(needle))
    .slice(0, limit)
}

const tools = [
  {
    name: 'get_daily_job_recommendations',
    description: '获取 OfferiGo 当日推荐的岗位列表（含岗位名称、公司、地点、薪资、匹配分）',
    inputSchema: {
      type: 'object',
      properties: {
        limit: { type: 'number', description: '最多返回条数，默认 10' }
      }
    }
  },
  {
    name: 'search_resume_materials',
    description: '按关键词检索候选人自己的简历素材库（项目、实习、技能、教育等），用于追问经历细节或核对事实',
    inputSchema: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '检索关键词，如技术栈、项目名、业务领域' },
        limit: { type: 'number', description: '最多返回条数，默认 8' }
      },
      required: ['keyword']
    }
  },
  {
    name: 'search_target_jobs',
    description: '按关键词检索候选人收藏的目标岗位及其岗位要求',
    inputSchema: {
      type: 'object',
      properties: {
        keyword: { type: 'string', description: '检索关键词，如岗位名、公司、技能' },
        limit: { type: 'number', description: '最多返回条数，默认 8' }
      },
      required: ['keyword']
    }
  }
]

const server = new Server(
  { name: 'offerigo', version: '0.1.0' },
  { capabilities: { tools: {} } }
)

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools }))

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params
  const limit = Number(args.limit) > 0 ? Number(args.limit) : 8
  const keyword = args.keyword ? String(args.keyword) : ''

  try {
    let data
    if (name === 'get_daily_job_recommendations') {
      data = pickByKeyword(await apiGet('/api/job/recommend/today'), '', limit)
    } else if (name === 'search_resume_materials') {
      data = pickByKeyword(await apiGet('/api/jobmatch/knowledge'), keyword, limit)
    } else if (name === 'search_target_jobs') {
      data = pickByKeyword(await apiGet('/api/jobmatch/jobs'), keyword, limit)
    } else {
      throw new Error(`未知工具：${name}`)
    }
    return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
  } catch (error) {
    return {
      content: [{ type: 'text', text: `调用失败：${error.message}` }],
      isError: true
    }
  }
})

const transport = new StdioServerTransport()
await server.connect(transport)
console.error(`OfferiGo MCP Server 已启动，后端地址 ${API_BASE}`)