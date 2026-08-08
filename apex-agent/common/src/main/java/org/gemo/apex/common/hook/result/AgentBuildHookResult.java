package org.gemo.apex.common.hook.result;

public sealed interface AgentBuildHookResult extends LifecycleHookResult
        permits ContinueAgentBuild {}
