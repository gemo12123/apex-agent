# SuperAgent Coordinator 重构设计

Date: 2026-05-24
Status: Draft for review

## 1. 背景

当前 Agent 会话执行链路已经拆出一部分应用层职责，但整体边界仍然不够稳定。

现状大致如下：

- `ChatController` 负责请求校验，并通过 `SessionExecutionGuard` 保护同一 `sessionId` 不并发执行
- `ChatStreamingApplicationService` 负责异步提交、上下文创建或恢复、执行 `SuperAgent`、发送终态 SSE、释放 guard
- `SuperAgentFactory` 目前只是一个 facade，实际委托给 `SuperAgentSessionService` 和 `SuperAgentExecutor`
- `SuperAgent` 仍然是 Spring 管理的单例组件，执行时依赖外部传入 `SuperAgentContext`
- `SuperAgentLoopRunner` 负责主循环
- `ExecutionFinalizer` 负责 turn 结束后的持久化收尾

这套结构比早期版本清晰，但仍然存在两个核心问题：

1. 会话“正在执行”的状态只是一个布尔 guard，而不是一个真实执行实例
2. `SuperAgent` 不是一个真正的会话执行对象，执行上下文与执行对象是分离的

这使得执行链路在概念上仍然偏散：

- controller、service、factory、executor、agent 之间职责切分过细
- 一次会话运行没有单一所有者
- 运行态无法直接通过 `sessionId` 找到对应的执行对象

## 2. 问题总结

### 2.1 会话并发控制过于薄弱

`SessionExecutionGuard` 只保存 `sessionId` 是否在运行，不能表达“谁在运行”。

这导致：

- 无法从运行表直接拿到执行实例
- 后续如果要扩展观测、取消、调试能力，guard 无法承载
- guard 的存在价值只剩“占坑”，语义偏弱

### 2.2 `SuperAgent` 不是会话实例

当前 `SuperAgent` 是一个 Spring 单例执行器，`SuperAgentContext` 则是外部创建后传入。

这意味着：

- `SuperAgent` 本身不拥有自己的上下文
- 执行对象与会话状态分离
- `sessionId -> agent` 的运行时映射无法自然建立

### 2.3 执行编排仍然散落在多个对象之间

一次 chat SSE 请求目前涉及：

- `ChatController`
- `ChatStreamingApplicationService`
- `SessionExecutionGuard`
- `SuperAgentFactory`
- `SuperAgentSessionService`
- `SuperAgentExecutor`
- `SuperAgent`
- `SuperAgentLoopRunner`
- `ExecutionFinalizer`

虽然其中部分对象本身职责单一，但从会话执行编排的视角看，链路过长。

### 2.4 SSE 终态处理与执行实例没有直接绑定

现在终态 SSE 由应用服务负责构造和发送，执行对象本身不拥有“这次会话运行”的完整生命周期语义。

## 3. 目标

- 引入统一的 `Coordinator` 作为会话执行协调器
- 用 `sessionId -> SuperAgent` 映射替代 `SessionExecutionGuard`
- 将 `SuperAgent` 改造成每次运行新建的普通对象，实例内持有 `SuperAgentContext`
- 由 `SuperAgentFactory` 统一负责上下文创建/恢复与 `SuperAgent` 组装
- 让 `ChatController -> ChatService -> Coordinator` 成为唯一 chat 执行入口
- 删除当前多余的中间执行包装层
- 在执行结束、失败、挂起三种情况下都统一发送空 `EndMessage`

## 4. 非目标

- 不修改现有消息流中的非终态 SSE 协议
- 不引入取消执行、超时中断、强制终止等新能力
- 不改变 `SuperAgentContextStore` 的持久化模型
- 不在本次重构中调整工具调用、memory recall、stage 切换等核心执行语义
- 不为 `END` 增加状态字段、错误字段或额外上下文

## 5. 关键决策

### 5.1 `Coordinator` 持有真实运行实例

`Coordinator` 内部使用 `ConcurrentHashMap<String, SuperAgent>` 保存运行中的 Agent：

- key: `sessionId`
- value: 当前正在运行的 `SuperAgent`

这使 `Coordinator` 成为会话执行的唯一运行态所有者。

除此之外，`Coordinator` 还需要维护第二个 map：

- key: `sessionId`
- value: 该会话对应的锁对象

该锁只用于保护“创建/恢复上下文并注册运行实例”这段临界区，目的不是替代 `runningAgents`，而是避免在上下文创建本身已经带有持久化副作用的情况下发生数据污染。

### 5.2 `SuperAgent` 改为普通类

`SuperAgent` 不再由 Spring 直接管理，不再是单例组件。

改造后：

- 每次请求执行时创建一个新实例
- 实例构造时注入 `SuperAgentContext`
- 实例内部持有本次运行需要的依赖
- 对外只暴露 `run()` 和 `getContext()`

### 5.3 `SuperAgentFactory` 直接 `new SuperAgent`

`SuperAgentFactory` 对外只暴露一个 `create(ChatRequest request, SseEmitter emitter)` 方法。

内部：

- `createForNew(...)` 负责新建会话或开启下一轮
- `createForResume(...)` 负责从挂起态恢复
- 两者都只作为 private 方法存在
- 最终由 factory 直接 `new SuperAgent(...)`

### 5.4 删除 `ChatStreamingApplicationService`

`ChatStreamingApplicationService` 不再存在。

其职责分拆为：

- `ChatService` 负责创建 `SseEmitter` 并调用 `Coordinator`
- `SuperAgentCoordinator` 负责执行编排、异步提交、终态发送与清理

### 5.5 删除 `SessionExecutionGuard`

guard 被 `Coordinator` 的运行表完全替代。

并发控制规则改为两级：

- 一级：`sessionId -> lock`，保护同一会话的创建/恢复临界区
- 二级：`runningAgents`，表示真正处于运行态的 `SuperAgent`

原因是当前 `SuperAgentSessionService.createContext()` 在返回前就会：

- 创建新 turn
- 追加用户消息
- 立即调用 `appendDialogueMessages(...)`
- 立即调用 `save(...)`

因此如果先执行 `factory.create(...)`，再判断 `runningAgents`，被拒绝的并发请求也可能已经把新一轮 turn 写入存储，造成会话历史污染。

为避免这个问题，必须保证：

- 同一 `sessionId` 下，`factory.create(...)` 只能在会话锁保护下执行
- `runningAgents` 的注册也必须在同一临界区内完成
- 只有注册成功后，临界区才可以释放

这样后到请求即使进入，也只会在锁外等待；等它拿到锁时，前一个请求要么已经注册为运行中实例，要么已经完全失败并清理，不会出现“请求被拒绝但已经写脏会话”的情况。

### 5.6 删除 `SuperAgentLoopRunner`

原主循环直接内聚进 `SuperAgent`。

`SuperAgent` 仍然可以依赖现有协作者：

- `StageToolResolver`
- `AgentPromptAssembler`
- `ModelResponseStreamer`
- `ToolInterceptor`
- `ToolCallProcessor`
- `ConversationMemoryManager`
- `HumanInLoopResumer`
- `MemoryLifecycleManager`
- `SessionContextStore`

但不再额外保留一个单独的 loop runner 包装层。

### 5.7 删除 `ExecutionFinalizer`

turn 结束后的收尾逻辑直接内聚到 `SuperAgent` 私有方法中。

包括：

- 持久化尚未落库的对话消息
- 更新 `nextMessageSortNo`
- 更新 `lastActiveTime`
- 保存 `SuperAgentContext`
- 触发 `memoryLifecycleManager.onTurnCompleted(context)`

### 5.8 `END` 始终发送空消息

终态 SSE 不再携带任何执行状态、错误码、mode 或 stage 信息。

统一发送：

```java
EndMessage.builder().build()
```

并通过 `MessageUtils.sendMessage(...)` 直接发送。

前端接受这一约束：

- 完成 / 失败 / 挂起 的区分不依赖 `END` 内容
- `END` 仅表示本次 SSE 流结束

### 5.9 挂起时立即移出运行表

当执行进入 `HUMAN_IN_THE_LOOP` 时：

- 当前 `SuperAgent` 完成本轮收尾并结束
- `Coordinator` 发送空 `END`
- `Coordinator` 立即从 map 中移除该 `SuperAgent`
- 只将状态保存在 `SuperAgentContextStore`
- 下次恢复时重新创建新的 `SuperAgent` 实例

## 6. 目标结构

### 6.1 `ChatController`

职责：

- 只做字段校验
- 调用 `ChatService`
- 返回 `SseEmitter`

不再负责：

- 并发控制
- 异步执行
- 终态发送
- 任何执行链路编排

### 6.2 `ChatService`

职责：

- 创建 `SseEmitter`
- 调用 `SuperAgentCoordinator.run(request, emitter)`
- 将 `SseEmitter` 返回给 controller

它是一个非常薄的应用入口适配层。

### 6.3 `SuperAgentCoordinator`

职责：

- 持有 `runningAgents`
- 持有 `sessionId -> lock` 映射
- 作为唯一运行入口
- 负责 session 并发校验
- 调用 factory 创建 `SuperAgent`
- 注册运行实例
- 提交线程池异步执行
- 在执行结束后统一发送空 `END`
- 执行 `emitter.complete()`
- 释放运行表中的实例

这是本次重构的核心对象。

### 6.4 `SuperAgentFactory`

职责：

- 对外暴露单一 `create(...)`
- 内部根据请求类型选择新建或恢复
- 创建或恢复 `SuperAgentContext`
- 将 `SseEmitter` 写入 context
- 组装 `SuperAgent` 所需依赖
- 直接 `new SuperAgent(...)`

### 6.5 `SuperAgent`

职责：

- 持有 `SuperAgentContext`
- 恢复 human-in-the-loop 状态
- 执行 Agent 主循环
- 处理运行时异常与挂起异常
- 执行 turn 收尾与持久化

`SuperAgent` 是“一次会话执行实例”，而不是通用执行器。

## 7. 运行流程

### 7.1 新建请求

`ChatController`:

1. 校验 `sessionId` 和请求字段
2. 调用 `ChatService.chat(request)`

`ChatService`:

1. 创建 `SseEmitter`
2. 调用 `coordinator.run(request, emitter)`
3. 返回 `emitter`

`SuperAgentCoordinator`:

1. 根据 `sessionId` 获取或创建会话锁对象
2. 进入该会话锁的临界区
3. 检查 `runningAgents` 中是否已存在该 `sessionId`
4. 若存在则立即拒绝，不执行 `factory.create(...)`
5. 若不存在，调用 `superAgentFactory.create(request, emitter)`
6. 获取 `SuperAgent` 和其内部 `context`
7. 在同一临界区内将实例注册到 `runningAgents`
8. 离开临界区
9. 提交线程池执行 `agent.run()`
10. 执行结束后发送空 `END`
11. `emitter.complete()`
12. 从 `runningAgents` 中移除实例

### 7.2 恢复请求

恢复请求与新建请求共用同一入口。

差别只在 factory 内部：

- `NEW` 走 `createForNew(...)`
- `HUMAN_RESPONSE` 走 `createForResume(...)`

恢复成功后，同样创建一个新的 `SuperAgent` 实例运行。

## 8. `SuperAgent` 内部执行模型

### 8.1 对外接口

建议 `SuperAgent` 对外只保留：

- `void run()`
- `SuperAgentContext getContext()`

### 8.2 `run()` 生命周期

`run()` 建议固定为：

1. `humanInLoopResumer.resume(context)`
2. 执行主循环
3. 捕获 `HumanInTheLoopException`
4. 捕获其他 `RuntimeException`
5. `finally` 中执行 `finalizeTurn()`

### 8.3 主循环

原 `SuperAgentLoopRunner.run(context)` 逻辑直接迁入 `SuperAgent` 私有方法，例如 `executeLoop()`。

主循环语义保持不变：

- 最多迭代固定次数
- 组装 prompt
- 请求模型流式输出
- 追加 assistant message
- 处理 tool call
- 没有 tool call 时结束
- 达到上限时记录日志
- 若仍为 `IN_PROGRESS`，最终置为 `COMPLETED`

### 8.4 异常处理

#### `HumanInTheLoopException`

- 不转为失败
- 保持当前挂起语义
- 继续进入 `finally`

#### 其他 `RuntimeException`

- 如果当前状态仍是 `IN_PROGRESS`，改为 `FAILED`
- 记录错误日志
- 继续向外抛出

### 8.5 收尾逻辑

`finalizeTurn()` 内部完成：

1. 持久化尚未落库的对话消息
2. 更新 `nextMessageSortNo`
3. 更新 `lastActiveTime`
4. 保存 `context`
5. 触发 memory 生命周期回调

这部分逻辑替代原 `ExecutionFinalizer`。

## 9. 协调器异常模型

### 9.1 并发冲突

如果同一 `sessionId` 已存在运行中的 `SuperAgent`：

- 拒绝本次请求
- 不执行 `factory.create(...)`
- 不提交线程池
- 不覆盖 map 中的原实例

该错误仍然是同步错误。

这里的关键要求是：

- 冲突检查必须发生在会话锁保护下
- 冲突检查必须先于上下文创建

否则会再次引入“拒绝了请求，但该请求已经持久化了新 turn”的数据污染问题。

### 9.2 创建失败

如果 `SuperAgentFactory.create(...)` 失败：

- 直接对当前 emitter 发送空 `END`
- `emitter.complete()`
- 不向 map 中写入任何实例

如果失败发生在持有会话锁期间：

- 必须在释放锁前结束本次创建流程
- 但不能在失败路径中留下虚假的运行态记录
- 锁释放后，后续请求可以重新尝试

### 9.3 线程池提交失败

如果提交线程池失败：

- 发送空 `END`
- `emitter.complete()`
- 立即从 map 中删除已注册实例

### 9.4 运行时失败

如果 `SuperAgent.run()` 抛出运行时异常：

- `SuperAgent` 自己负责把状态调整为 `FAILED`
- `Coordinator` 不再区分终态类型
- 仅发送空 `END`
- 然后清理 emitter 和 map

### 9.5 挂起结束

如果 `SuperAgent.run()` 在内部进入 `HUMAN_IN_THE_LOOP`：

- 本轮仍然视为执行结束
- `Coordinator` 发送空 `END`
- `Coordinator` 立即移除 map 中实例
- 恢复依赖持久化状态，而不是复用旧实例

## 10. 建议的方法签名

### 10.1 `ChatService`

```java
public SseEmitter chat(ChatRequest request)
```

### 10.2 `SuperAgentCoordinator`

```java
public void run(ChatRequest request, SseEmitter emitter)
private Object getSessionLock(String sessionId)
private SuperAgent createAndRegisterAgent(ChatRequest request, SseEmitter emitter)
private void executeAsync(String sessionId, SuperAgent agent)
private void doRun(String sessionId, SuperAgent agent)
private void sendEnd(SseEmitter emitter)
private void cleanup(String sessionId, SuperAgent agent, SseEmitter emitter)
```

### 10.3 `SuperAgentFactory`

```java
public SuperAgent create(ChatRequest request, SseEmitter emitter)
private SuperAgent createForNew(ChatRequest request, SseEmitter emitter)
private SuperAgent createForResume(ChatRequest request, SseEmitter emitter)
```

### 10.4 `SuperAgent`

```java
public void run()
public SuperAgentContext getContext()
private void executeLoop()
private void finalizeTurn()
private void persistDialogueMessages()
```

## 11. 删除与迁移清单

### 11.1 删除的类

- `SessionExecutionGuard`
- `ChatStreamingApplicationService`
- `SuperAgentLoopRunner`
- `ExecutionFinalizer`
- `ChatTerminalEventFactory`

### 11.2 修改的类

- `ChatController`
- `ChatService`（新建或由现有 service 演进）
- `SuperAgentFactory`
- `SuperAgent`

### 11.3 保留但职责不变的协作者

- `SuperAgentSessionService`
- `StageToolResolver`
- `AgentPromptAssembler`
- `ModelResponseStreamer`
- `ToolInterceptor`
- `ToolCallProcessor`
- `ConversationMemoryManager`
- `HumanInLoopResumer`
- `SessionContextStore`
- `MemoryLifecycleManager`

## 12. 测试策略

需要补充或重写以下测试：

- controller 仅做字段校验和委派
- `ChatService` 创建 `SseEmitter` 并调用 coordinator
- `Coordinator` 在新建请求上的成功路径
- `Coordinator` 在恢复请求上的成功路径
- `Coordinator` 的并发冲突路径
- `Coordinator` 在并发冲突下不会调用 `factory.create(...)`
- `Coordinator` 在两个并发请求使用同一 `sessionId` 时，后到请求不会写入新 turn
- `Coordinator` 的 factory 创建失败路径
- `Coordinator` 的线程池提交失败路径
- `Coordinator` 的运行时失败路径
- `Coordinator` 在挂起场景下会发送 `END` 并移除 map
- `SuperAgent` 主循环正常完成
- `SuperAgent` 在 `HumanInTheLoopException` 下仍然完成收尾
- `SuperAgent` 在运行时异常下会标记 `FAILED` 并完成收尾

## 13. 风险与权衡

### 13.1 `SuperAgent` 会变胖

这是本次设计的显式选择。

因为你希望：

- `SuperAgent` 成为真正的会话实例
- 删除 `SuperAgentLoopRunner`
- 删除 `ExecutionFinalizer`

因此 `SuperAgent` 必然承担更多内部编排。

当前设计通过“保留协作者、只内聚主循环和收尾”来控制体积。

### 13.2 `END` 不再表达结果

删除 `END` 上下文后，终态消息退化为“流结束标记”。

这会降低终态可观测性，但这是本次确认过的产品约束，因此按该约束实施。

### 13.3 运行表必须使用实例匹配删除

清理时建议使用：

```java
runningAgents.remove(sessionId, agent)
```

避免在极端竞态下误删后续新实例。

### 13.4 锁对象 map 需要有清理策略

如果使用 `sessionId -> lock` 的永久 map，而不做回收，会导致锁对象数量只增不减。

因此建议：

- 使用 `computeIfAbsent(sessionId, ...)` 获取锁对象
- 在执行清理阶段尝试移除空闲锁对象
- 只有当该 `sessionId` 不在 `runningAgents` 中，且当前没有线程仍持有该锁时，才移除对应锁对象

如果实现上不方便安全回收，则保留锁对象 map 也可以接受，但需要在实现阶段明确这是一个受控的内存换简化方案。

## 14. 决策摘要

本次重构的最终方向是：

- `ChatController` 只校验
- `ChatService` 只创建 `SseEmitter` 并调用 `Coordinator`
- `SuperAgentCoordinator` 持有 `sessionId -> SuperAgent` 运行表，并统一负责执行编排
- `SuperAgentFactory` 对外仅暴露 `create(...)`，内部完成 context 创建/恢复并直接 `new SuperAgent(...)`
- `SuperAgent` 改为普通类，实例内持有 `SuperAgentContext`
- 删除 `SessionExecutionGuard`
- 删除 `ChatStreamingApplicationService`
- 删除 `SuperAgentLoopRunner`
- 删除 `ExecutionFinalizer`
- 删除 `ChatTerminalEventFactory`
- 所有终态统一发送空 `EndMessage`
- 挂起后立即移出运行表，恢复时重新创建实例

这是当前目标下最直接、最一致的一版会话执行模型。
