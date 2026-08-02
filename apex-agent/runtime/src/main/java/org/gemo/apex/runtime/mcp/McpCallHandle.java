package org.gemo.apex.runtime.mcp;import java.util.*;public interface McpCallHandle extends AutoCloseable{Map<String,Object>await();void cancel();default void close(){}}
