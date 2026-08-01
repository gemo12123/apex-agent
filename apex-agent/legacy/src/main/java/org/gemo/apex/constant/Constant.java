package org.gemo.apex.constant;

import java.time.format.DateTimeFormatter;

public class Constant {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final String PLAN_TOOL_NAME = ToolNames.WRITE_PLAN;
    public static final String REPLAN_TOOL_NAME = ToolNames.UPDATE_PLAN;
    public static final String SKILLS_TOOL_NAME = ToolNames.ACTIVATE_SKILL;
}
