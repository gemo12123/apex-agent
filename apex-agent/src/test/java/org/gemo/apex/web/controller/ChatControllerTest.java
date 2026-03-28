package org.gemo.apex.web.controller;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.SuperAgentFactory;
import org.gemo.apex.memory.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SuperAgentFactory superAgentFactory;

    @InjectMocks
    private ChatController chatController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        UserContextHolder.clear();
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
    }

    @AfterEach
    public void tearDown() {
        UserContextHolder.clear();
        Object executor = ReflectionTestUtils.getField(chatController, "executorService");
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }

    @Test
    public void testExecuteWithSse_ReturnsSseEmitter() throws Exception {
        SuperAgentContext mockContext = new SuperAgentContext();
        when(superAgentFactory.createContext(anyString(), anyString(), anyString())).thenReturn(mockContext);
        doNothing().when(superAgentFactory).executeContext(any(SuperAgentContext.class));

        String jsonRequest = """
                {
                  "sessionId":"session-123",
                  "agentKey":"default_agent",
                  "query":"Hello Super Agent",
                  "type":"NEW"
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/sse/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());
    }

    @Test
    public void testExecuteWithSse_NewRequestRejectedForSuspendedSession() throws Exception {
        when(superAgentFactory.createContext(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException(
                        "Session session-123 is waiting for HUMAN_RESPONSE; RequestType.NEW is not allowed"));

        String jsonRequest = """
                {
                  "sessionId":"session-123",
                  "agentKey":"default_agent",
                  "query":"Hello Super Agent",
                  "type":"NEW"
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/sse/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(superAgentFactory, never()).resumeContext(anyString(), any());
    }

    @Test
    public void testExecuteWithSse_HumanResponseUsesResumeContext() throws Exception {
        SuperAgentContext mockContext = new SuperAgentContext();
        when(superAgentFactory.resumeContext(anyString(), any())).thenReturn(mockContext);
        doNothing().when(superAgentFactory).executeContext(any(SuperAgentContext.class));

        String jsonRequest = """
                {
                  "sessionId":"session-123",
                  "agentKey":"default_agent",
                  "type":"HUMAN_RESPONSE",
                  "humanResponse":{"tool-call-1":"approved"}
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/sse/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(superAgentFactory).resumeContext(anyString(), any());
        verify(superAgentFactory, never()).createContext(anyString(), anyString(), anyString());
    }
}
