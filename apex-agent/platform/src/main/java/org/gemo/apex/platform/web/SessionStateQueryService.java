package org.gemo.apex.platform.web;

import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.protocol.request.SessionStateView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionStateQueryService {
    private final SessionRepository sessions;
    private final SessionStateViewMapper mapper = new SessionStateViewMapper();

    public SessionStateQueryService(SessionRepository sessions) { this.sessions = sessions; }

    public SessionStateView query(String sessionId, String agentKey, String userId) {
        var snapshot = sessions.load(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!snapshot.userId().equals(userId) || !snapshot.agentKey().equals(agentKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            return mapper.map(snapshot);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "会话快照不完整", exception);
        }
    }
}
