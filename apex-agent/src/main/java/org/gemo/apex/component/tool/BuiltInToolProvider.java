package org.gemo.apex.component.tool;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.tool.AskHumanTool;
import org.gemo.apex.tool.UpdatePlanTool;
import org.gemo.apex.tool.WritePlanTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 内置工具提供者
 * 将所有的核心内置工具统一封装为 ToolCallback 列表缓存
 */
@Slf4j
@Component
public class BuiltInToolProvider {

    private List<ToolCallback> builtInTools;

    @PostConstruct
    public void init() {
        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(
                        new AskHumanTool(),
                        new WritePlanTool(),
                        new UpdatePlanTool())
                .build();

        this.builtInTools = Collections.unmodifiableList(
                new ArrayList<>(Arrays.asList(provider.getToolCallbacks())));

        log.info("BuiltInToolProvider 初始化完成，共注册 {} 个内置核心工具", this.builtInTools.size());
    }

    /**
     * 获取全部的内置核工具回调
     */
    public List<ToolCallback> getBuiltInTools() {
        return this.builtInTools;
    }
}
