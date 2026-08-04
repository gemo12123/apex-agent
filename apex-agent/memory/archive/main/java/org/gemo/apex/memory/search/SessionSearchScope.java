package org.gemo.apex.memory.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSearchScope {
    private String userId;
    private String agentKey;
}
