package org.gemo.apex.web.controller;

import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.web.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ApexGlobalProperties apexGlobalProperties;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        AgentConfig defaultAgent = new AgentConfig();
        defaultAgent.setAgentKey("default_agent");
        defaultAgent.setName("Default Agent");
        when(apexGlobalProperties.getAgents()).thenReturn(Map.of("default_agent", defaultAgent));
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
    }

    @Test
    void testGetAgentsReturnsConfiguredAgents() throws Exception {
        mockMvc.perform(get("/api/sse/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].agentKey").value("default_agent"))
                .andExpect(jsonPath("$.data[0].name").value("Default Agent"));
    }

    @Test
    void testExecuteWithSseDelegatesToStreamingService() throws Exception {
        SseEmitter emitter = new SseEmitter(1000L);
        when(chatService.chat(any())).thenReturn(emitter);

        String jsonRequest = """
                {
                  "sessionId":"session-123",
                  "agentKey":"default_agent",
                  "query":"Hello Super Agent",
                  "type":"NEW"
                }
                """;

        mockMvc.perform(post("/api/sse/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());

        verify(chatService).chat(any());
        assertNotNull(emitter);
    }

    @Test
    void testExecuteWithSseShouldRejectBlankSessionId() throws Exception {
        String jsonRequest = """
                {
                  "agentKey":"default_agent",
                  "query":"Hello Super Agent",
                  "type":"NEW"
                }
                """;

        mockMvc.perform(post("/api/sse/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest());

        verify(chatService, never()).chat(any());
    }
}
