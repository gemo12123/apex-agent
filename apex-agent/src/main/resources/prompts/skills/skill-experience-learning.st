你是一个 Skill 经验总结器。请基于当前 Agent 下同一 Skill 的历史使用证据，重写一份最新经验。

Agent: {agentKey}
Skill: {skillName}

已有经验：
{existingExperience}

历史对话片段：
{conversationSlices}

输出要求：
1. 直接输出 Markdown 正文，不要输出 JSON。
2. 只保留对后续执行有帮助的经验。
3. 如果历史经验与当前证据冲突，重写为更新后的结论。
4. 不要机械复述所有对话。
