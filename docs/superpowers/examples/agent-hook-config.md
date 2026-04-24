# Agent Hook Config Examples

## Global config in `application.yml`

```yaml
apex:
  global:
    agents:
      default_agent:
        hooks:
          pre-tool-call:
            - bean: toolConfirmHook
              enabled: true
              order: 100
              tools: ["meeting_tool"]
              options:
                title: "预订会议室前确认"
                description: "请确认会议信息。"
                tool-display-name: "会议室助手"
                confirm-label: "确认执行"
                deny-label: "取消"
                display-fields:
                  - key: room
                    label: "会议室"
                  - key: date
                    label: "会议日期"
                editable-fields:
                  - key: room
                    label: "会议室"
                    input-type: "single-select"
                    required: true
                    options:
                      - label: "A1001"
                      - label: "B2001"
          post-tool-call:
            - bean: plainTextTruncateHook
              enabled: true
              order: 200
              tools: ["*"]
              options:
                max-length: 4000
```

## Workspace override in `agents/<agentKey>/config.yaml`

```yaml
default-execution-mode: REACT
allow-mcps: ["meeting-server"]
hooks:
  pre-tool-call:
    - bean: toolConfirmHook
      enabled: true
      tools: ["meeting_tool"]
      options:
        title: "会议室调用确认"
        tool-display-name: "会议室助手"
        display-fields:
          - key: room
            label: "会议室"
        editable-fields:
          - key: room
            label: "会议室"
            input-type: "single-select"
            options:
              - label: "A1001"
              - label: "B2001"
```

## Disable all hooks for one agent

```yaml
hooks: []
```
