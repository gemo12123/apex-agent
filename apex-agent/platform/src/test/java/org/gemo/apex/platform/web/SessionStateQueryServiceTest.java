package org.gemo.apex.platform.web;

import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.platform.PlatformFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SessionStateQueryServiceTest {
    /**
     * 查询只读取一次Repository并统一隐藏归属错误
     */
    @Test
    void readsRepositoryOnceAndConsistentlyHidesOwnershipErrors() {
        AtomicInteger loads = new AtomicInteger();
        SessionRepository repository = new SessionRepository() {
            @Override public Optional<org.gemo.apex.common.snapshot.SessionSnapshot> load(String sessionId) {
                loads.incrementAndGet();
                return Optional.of(PlatformFixtures.suspendedSnapshot());
            }
            @Override public void save(org.gemo.apex.common.snapshot.SessionSnapshot snapshot) {
                fail("只读查询不得保存");
            }
        };
        var service = new SessionStateQueryService(repository);
        assertNotNull(service.query("session-1", "default", "user-1").pendingInteraction());
        assertEquals(1, loads.get());
        ResponseStatusException hidden = assertThrows(ResponseStatusException.class,
                () -> service.query("session-1", "default", "other-user"));
        assertEquals(404, hidden.getStatusCode().value());
    }
}
