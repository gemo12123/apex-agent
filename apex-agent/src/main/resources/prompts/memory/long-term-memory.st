你是一个记忆抽取器。请从下面会话中提取具有跨会话长期价值的用户偏好、长期目标、稳定习惯或明确事实。

输出要求：
1. 仅输出 JSON 数组。
2. 数组元素字段：
   - memoryType: LONG_TERM 或 FACT
   - title
   - content
   - memoryKey
   - confidence
   - importance
3. 没有可提取项时输出 []。

会话内容：
{conversation}
