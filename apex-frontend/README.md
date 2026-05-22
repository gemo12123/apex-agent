# apex-frontend

`apex-frontend` 是 `apex-agent` 的配套工作台前端，基于 Vue 3、TypeScript、Pinia 和 Vite。

它不是默认模板页，而是已经围绕后端 SSE 协议实现了一套会话工作区，用于展示：

- 用户消息与模型正文流
- 计划阶段
- 工具调用轨迹
- 结构化产物
- `ASK_HUMAN` 交互
- `TOOL_CONFIRMATION` 交互

## 技术栈

- Vue 3
- TypeScript
- Pinia
- Vue Router
- Vite
- Vitest
- `@microsoft/fetch-event-source`

## 开发脚本

```bash
npm run dev
npm run build
npm run test
npm run test:run
npm run typecheck
```

## 本地开发

### 1. 安装依赖

```bash
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

默认地址通常为：

- `http://localhost:5173`

### 3. 后端代理

开发环境里，前端把 `/apex-api/*` 代理到：

- `http://localhost:18083/api/*`

也就是说，前端默认依赖本地后端：

```bash
cd ../apex-agent
mvn spring-boot:run
```

## 目录结构

- `src/features/workspace/pages/WorkspacePage.vue`
  页面级工作台入口
- `src/features/workspace/components/*`
  聊天区、执行轨、详情面板、欢迎页、确认卡片等组件
- `src/stores/session/store.ts`
  会话生命周期、请求发送、恢复逻辑
- `src/stores/session/reducer.ts`
  SSE envelope -> 前端视图模型的状态归并器
- `src/services/apex-api.ts`
  Agent 列表获取与 SSE 会话请求
- `src/types/apex.ts`
  前后端共享事件模型的前端类型定义

## 当前交互模型

### 发起会话

前端先请求：

- `GET /apex-api/sse/agents`

然后通过：

- `POST /apex-api/sse/chat`

发起 `NEW` 类型会话。

### 恢复挂起会话

前端收到以下事件时会进入挂起态：

- `ASK_HUMAN`
- `TOOL_CONFIRMATION`

用户提交结果后，前端会再次调用：

- `POST /apex-api/sse/chat`

并把 `type` 切换为 `HUMAN_RESPONSE`。

### SSE 状态消费

前端当前重点消费这些事件：

- `STREAM_CONTENT`
- `PLAN_DECLARED`
- `PLAN_CHANGE`
- `INVOCATION_DECLARED`
- `INVOCATION_CHANGE`
- `ARTIFACT_DECLARED`
- `ARTIFACT_CHANGE`
- `ASK_HUMAN`
- `TOOL_CONFIRMATION`
- `END`

前端仍保留了对 `STREAM_THINK` 的兼容，但当前主链路主要展示正文流。

## 测试

当前已经覆盖了一批核心前端行为测试，包括：

- API client
- session store
- reducer
- 工作台组件
- 人工交互与工具确认组件

如果你要改 SSE 协议消费逻辑，优先一起看这些测试：

- `src/stores/session/reducer.test.ts`
- `src/stores/session/store.test.ts`
- `src/features/workspace/components/*.test.ts`
