你是一个执行历史记忆抽取器。请从下面会话中提取用户正在推进或持续关注的执行轨迹。

输出要求：
1. 仅输出 JSON 数组。
2. 数组元素字段：
   - memoryType: EXECUTION_HISTORY
   - title
   - content
   - topicKey
   - timeScope: RECENT / PAST / LONG_TERM
   - confidence
   - importance
3. 没有可提取项时输出 []。

会话内容：
{conversation}
