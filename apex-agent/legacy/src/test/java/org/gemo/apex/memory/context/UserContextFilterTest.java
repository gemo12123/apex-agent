package org.gemo.apex.memory.context;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserContextFilterTest {

    private final UserContextFilter filter = new UserContextFilter();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void doFilter_readsUserIdFromHeaderAndClearsAfterward() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContextFilter.USER_ID_HEADER, "input-user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observedUserId = new AtomicReference<>();
        FilterChain chain = (req, res) -> observedUserId.set(UserContextHolder.getUserId());

        filter.doFilter(request, response, chain);

        assertEquals("input-user", observedUserId.get());
        assertNull(UserContextHolder.getUserId());
    }

    @Test
    void doFilter_rejectsRequestWhenUserIdHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(chainInvoked.get());
        assertEquals(MockHttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("Missing required header: " + UserContextFilter.USER_ID_HEADER, response.getErrorMessage());
        assertNull(UserContextHolder.getUserId());
    }
}
