package org.gemo.apex.memory.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSearchResult {
    private String query;
    private List<SessionSearchHit> hits;
}
