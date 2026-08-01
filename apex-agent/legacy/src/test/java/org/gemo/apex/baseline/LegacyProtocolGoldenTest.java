package org.gemo.apex.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.hook.tool.EditableFieldInputType;
import org.gemo.apex.hook.tool.ToolConfirmationDisplayField;
import org.gemo.apex.hook.tool.ToolConfirmationEditableField;
import org.gemo.apex.hook.tool.ToolConfirmationSpec;
import org.gemo.apex.message.AgentMessage;
import org.gemo.apex.message.AskHumanMessage;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.message.PlanChangeMessage;
import org.gemo.apex.message.PlanDeclaredMessage;
import org.gemo.apex.message.StreamContentMessage;
import org.gemo.apex.message.TaskThinkChangeMessage;
import org.gemo.apex.message.TaskThinkDeclaredMessage;
import org.gemo.apex.message.ToolConfirmationMessage;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyProtocolGoldenTest {

    @Test
    void streamContentMatchesGoldenFileAndReactContext() throws IOException {
        StreamContentMessage message = StreamContentMessage.builder()
                .context(Map.of("mode", "react", "content_id", "content-1"))
                .messages(List.of(new StreamContentMessage.ContentMessage("hello")))
                .build();

        assertGolden("stream-content.json", message, StreamContentMessage.class, true);
    }

    @Test
    void askHumanMatchesGoldenFileIncludingEmptyOptions() throws IOException {
        AskHumanMessage message = AskHumanMessage.builder()
                .context(Map.of("mode", "react", "executor", "ask_human", "invocation_id", "invocation-1"))
                .messages(List.of(AskHumanMessage.AskHumanDetail.builder()
                        .inputType("TEXT_INPUT")
                        .question("Need input?")
                        .options(List.of())
                        .toolCallId("call-ask-1")
                        .build()))
                .build();

        assertGolden("ask-human.json", message, AskHumanMessage.class, true);
    }

    @Test
    void toolConfirmationMatchesGoldenFileWithoutStageId() throws IOException {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionMode(ModeEnum.REACT);
        ToolConfirmationSpec spec = ToolConfirmationSpec.builder()
                .confirmationId("confirm-1")
                .toolName("meeting_tool")
                .toolDisplayName("Meeting Tool")
                .title("Confirm meeting")
                .description("Create a meeting")
                .riskLevel("medium")
                .editable(true)
                .confirmLabel("Approve")
                .denyLabel("Deny")
                .displayFields(List.of(ToolConfirmationDisplayField.builder()
                        .key("date").label("Date").value("2026-08-01").type("text").build()))
                .editableFields(List.of(ToolConfirmationEditableField.builder()
                        .key("room").label("Room").inputType(EditableFieldInputType.TEXT)
                        .value("A1001").required(true).options(List.of()).build()))
                .build();
        ToolConfirmationMessage message = ToolConfirmationMessage.from(
                context,
                new AssistantMessage.ToolCall("call-confirm-1", "function", "meeting_tool", "{}"),
                "invocation-2",
                spec);

        assertGolden("tool-confirmation.json", message, ToolConfirmationMessage.class, true);
    }

    @Test
    void endMatchesExactRawPayload() throws IOException {
        String json = JacksonUtils.toJson(EndMessage.builder().build());

        assertEquals(resource("golden/protocol/end.json"), json);
        assertEquals("{\"event_type\":\"END\"}", json);
        assertInstanceOf(EndMessage.class, JacksonUtils.fromJson(json, AgentMessage.class));
    }

    @Test
    void retainedPlanAndTaskThinkDtosMatchGoldenFiles() throws IOException {
        PlanDeclaredMessage planDeclared = PlanDeclaredMessage.builder()
                .context(Map.of("mode", "react"))
                .messages(List.of(PlanDeclaredMessage.StageMessage.builder()
                        .stageId("stage-1").stageName("Legacy stage")
                        .description("Compatibility DTO only").status("PENDING").build()))
                .build();
        PlanChangeMessage planChange = PlanChangeMessage.builder()
                .context(Map.of("mode", "react"))
                .messages(List.of(PlanChangeMessage.PlanChangeDetail.builder()
                        .changeType("STATUS_CHANGE").stageId("stage-1").status("COMPLETED").build()))
                .build();
        TaskThinkDeclaredMessage taskDeclared = TaskThinkDeclaredMessage.builder()
                .context(Map.of("mode", "react"))
                .messages(List.of(TaskThinkDeclaredMessage.TaskThinkDeclaredDetail.builder()
                        .taskId("task-1").content("thinking").build()))
                .build();
        TaskThinkChangeMessage taskChange = TaskThinkChangeMessage.builder()
                .context(Map.of("mode", "react"))
                .messages(List.of(TaskThinkChangeMessage.TaskThinkChangeDetail.builder()
                        .changeType("CONTENT_APPEND").taskId("task-1").content(" more").build()))
                .build();

        assertGolden("plan-declared.json", planDeclared, PlanDeclaredMessage.class, false);
        assertGolden("plan-change.json", planChange, PlanChangeMessage.class, false);
        assertGolden("task-think-declared.json", taskDeclared, TaskThinkDeclaredMessage.class, false);
        assertGolden("task-think-change.json", taskChange, TaskThinkChangeMessage.class, false);
    }

    @Test
    void baselineManifestIsReadableAndClassifiesLegacyOnlyCases() throws IOException {
        JsonNode manifest = JacksonUtils.toTree(resource("golden/scenarios/baseline-manifest.json"));

        assertEquals("1.0.0", manifest.path("schema_version").asText());
        assertEquals(7, manifest.path("cases").size());
        assertEquals(1, manifest.path("cases").findValuesAsText("category").stream()
                .filter("LEGACY_ONLY"::equals)
                .count());
    }

    private void assertGolden(String fileName, AgentMessage message, Class<? extends AgentMessage> expectedType,
            boolean assertNoStageId) throws IOException {
        String actualJson = JacksonUtils.toJson(message);
        JsonNode actual = JacksonUtils.toTree(actualJson);
        JsonNode expected = JacksonUtils.toTree(resource("golden/protocol/" + fileName));

        assertEquals(expected, actual);
        assertNotNull(actual.get("event_type"));
        JsonNode roundTripNode = actual.deepCopy();
        if (expectedType == ToolConfirmationMessage.class) {
            var detail = (com.fasterxml.jackson.databind.node.ObjectNode) roundTripNode.path("messages").get(0);
            detail.putArray("display_fields");
            detail.putArray("editable_fields");
        }
        assertInstanceOf(expectedType, JacksonUtils.fromJson(roundTripNode.toString(), AgentMessage.class));
        JsonNode withUnknownField = roundTripNode.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) withUnknownField).put("future_field", "ignored");
        assertInstanceOf(expectedType, JacksonUtils.fromJson(withUnknownField.toString(), AgentMessage.class));
        if (assertNoStageId) {
            assertEquals("react", actual.path("context").path("mode").asText());
            assertFalse(actual.path("context").has("stage_id"));
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }
}
