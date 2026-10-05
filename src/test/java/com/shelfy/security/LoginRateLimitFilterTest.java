package com.shelfy.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    private final LoginRateLimitFilter filter = new LoginRateLimitFilter();

    private void hitLogin(String ip, MockHttpServletResponse response) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        filter.doFilter(request, response, new MockFilterChain());
    }

    @Test
    void allowsRequestsUnderTheLimit() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 10; i++) {
            hitLogin("10.0.0.1", response);
        }

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksWithTooManyRequestsAfterTheLimitIsExceeded() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 11; i++) {
            response = new MockHttpServletResponse();
            hitLogin("10.0.0.2", response);
        }

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Demasiados intentos");
    }

    @Test
    void tracksEachIpIndependently() throws Exception {
        MockHttpServletResponse blockedIpResponse = new MockHttpServletResponse();
        for (int i = 0; i < 11; i++) {
            blockedIpResponse = new MockHttpServletResponse();
            hitLogin("10.0.0.3", blockedIpResponse);
        }
        assertThat(blockedIpResponse.getStatus()).isEqualTo(429);

        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        hitLogin("10.0.0.4", otherIpResponse);

        assertThat(otherIpResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotFilterOtherEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/books");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
