package org.gemo.apex.common.agent;

public sealed interface AgentDefinitionOperation
        permits AddAvailableTool,
                RemoveAvailableTool,
                ReplacePrompt,
                AppendPrefixDeveloperMessage,
                AddHookBinding,
                RemoveHookBinding {}
