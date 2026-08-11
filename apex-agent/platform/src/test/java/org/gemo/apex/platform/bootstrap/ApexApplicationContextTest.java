package org.gemo.apex.platform.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "spring.ai.mcp.client.enabled=false"
        })
@Import(ApexApplicationContextTest.ModelConfiguration.class)
class ApexApplicationContextTest {
    /** 应装配单一Runtime和完整Agent列表 */
    @Test
    void configuresSingleRuntimeAndCompleteAgentList(
            @Autowired ApexAgentRuntime runtime, @Autowired AgentDefinitionProvider definitions) {
        assertNotNull(runtime);
        assertEquals(
                List.of("default_agent", "mcp_agent"),
                definitions.listAgents().stream().map(value -> value.agentKey()).toList());
    }

    @TestConfiguration
    static class ModelConfiguration {
        @Bean
        ModelGateway modelGateway() {
            return (request, observer) -> new ModelResponse("完成", List.of(), Map.of());
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate(new DriverManagerDataSource("jdbc:invalid"));
        }
    }
}
