package org.gemo.apex.platform.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("apex.platform")
public class ApexAgentPlatformProperties {
    private Map<String, Agent> agents = new LinkedHashMap<>();
    private String definitionResource;
    private long sseTimeoutMillis = 600_000L;

    public Map<String, Agent> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, Agent> agents) {
        this.agents = agents;
    }

    public String getDefinitionResource() {
        return definitionResource;
    }

    public void setDefinitionResource(String definitionResource) {
        this.definitionResource = definitionResource;
    }

    public long getSseTimeoutMillis() {
        return sseTimeoutMillis;
    }

    public void setSseTimeoutMillis(long sseTimeoutMillis) {
        this.sseTimeoutMillis = sseTimeoutMillis;
    }

    public static class Agent {
        private String name;
        private String description;
        private Prompt prompt = new Prompt();
        private MessageCompression messageCompression = new MessageCompression();
        private Tools tools = new Tools();
        private Set<String> skills = Set.of();
        private Map<String, SubAgent> subAgents = Map.of();
        private Map<String, List<Hook>> hooks = Map.of();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Prompt getPrompt() {
            return prompt;
        }

        public void setPrompt(Prompt prompt) {
            this.prompt = prompt;
        }

        public MessageCompression getMessageCompression() {
            return messageCompression;
        }

        public void setMessageCompression(MessageCompression value) {
            this.messageCompression = value;
        }

        public Tools getTools() {
            return tools;
        }

        public void setTools(Tools tools) {
            this.tools = tools;
        }

        public Set<String> getSkills() {
            return skills;
        }

        public void setSkills(Set<String> skills) {
            this.skills = skills;
        }

        public Map<String, SubAgent> getSubAgents() {
            return subAgents;
        }

        public void setSubAgents(Map<String, SubAgent> subAgents) {
            this.subAgents = subAgents;
        }

        public Map<String, List<Hook>> getHooks() {
            return hooks;
        }

        public void setHooks(Map<String, List<Hook>> hooks) {
            this.hooks = hooks;
        }
    }

    public static class Prompt {
        private String system;
        private int maxIterations = 30;

        public String getSystem() {
            return system;
        }

        public void setSystem(String system) {
            this.system = system;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
    }

    public static class MessageCompression {
        private boolean enabled = true;
        private int maxMessages = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }
    }

    public static class Tools {
        private Set<String> available = Set.of();
        private Set<String> defaultEnabled = Set.of();

        public Set<String> getAvailable() {
            return available;
        }

        public void setAvailable(Set<String> available) {
            this.available = available;
        }

        public Set<String> getDefaultEnabled() {
            return defaultEnabled;
        }

        public void setDefaultEnabled(Set<String> defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
        }
    }

    public static class SubAgent {
        private String agentKey;
        private String description;

        public String getAgentKey() {
            return agentKey;
        }

        public void setAgentKey(String agentKey) {
            this.agentKey = agentKey;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class Hook {
        private String id;
        private String name;
        private int order;
        private boolean enabled = true;
        private List<String> tools = List.of();
        private Map<String, Object> options = Map.of();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getTools() {
            return tools;
        }

        public void setTools(List<String> tools) {
            this.tools = tools;
        }

        public Map<String, Object> getOptions() {
            return options;
        }

        public void setOptions(Map<String, Object> options) {
            this.options = options;
        }
    }
}
