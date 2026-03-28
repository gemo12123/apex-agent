package org.gemo.apex.memory.web;

import org.gemo.apex.memory.context.UserContextHolder;
import org.gemo.apex.memory.web.service.MemoryManageService;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryQueryType;
import org.gemo.apex.memory.web.dto.MemoryUpdateRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 记忆管理接口。
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryManageService memoryManageService;

    public MemoryController(MemoryManageService memoryManageService) {
        this.memoryManageService = memoryManageService;
    }

    @GetMapping(value = "/items", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> listItems(@RequestParam("type") MemoryQueryType type,
            @RequestParam("agentKey") String agentKey,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        List<MemoryItem> items = memoryManageService.listItems(type, UserContextHolder.getUserId(), agentKey, page, pageSize);
        return Map.of("code", 200, "data", items, "message", "success");
    }

    @PatchMapping(value = "/items/{memoryType}/{memoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateItem(@PathVariable("memoryType") MemoryQueryType memoryType,
            @PathVariable("memoryId") String memoryId,
            @RequestParam("agentKey") String agentKey,
            @RequestBody MemoryUpdateRequest request) {
        MemoryItem item = memoryManageService.updateContent(memoryType, memoryId, UserContextHolder.getUserId(),
                agentKey, request.getContent());
        return Map.of("code", 200, "data", item, "message", "success");
    }

    @DeleteMapping(value = "/items/{memoryType}/{memoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> deleteItem(@PathVariable("memoryType") MemoryQueryType memoryType,
            @PathVariable("memoryId") String memoryId,
            @RequestParam("agentKey") String agentKey) {
        memoryManageService.deleteItem(memoryType, memoryId, UserContextHolder.getUserId(), agentKey);
        return Map.of("code", 200, "message", "success");
    }

    @DeleteMapping(value = "/execution-history", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> clearExecutionHistory(@RequestParam("agentKey") String agentKey) {
        memoryManageService.clearExecutionHistory(UserContextHolder.getUserId(), agentKey);
        return Map.of("code", 200, "message", "success");
    }
}
