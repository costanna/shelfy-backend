package com.shelfy.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final Long USER_ID = 1L;

    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService userDetailsService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal principal(int tokenVersion) {
        return new UserPrincipal(USER_ID, "lectora@shelfy.app", "hash", tokenVersion);
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    void doesNothingWhenThereIsNoAuthorizationHeader() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void ignoresAHeaderThatIsNotABearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void authenticatesWhenTheTokenIsValidAndVersionMatches() throws Exception {
        when(jwtService.extractUserId("good-token")).thenReturn(USER_ID);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(principal(0));
        when(jwtService.extractTokenVersion("good-token")).thenReturn(0);

        filter.doFilter(requestWithBearer("good-token"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(((UserPrincipal) authentication.getPrincipal()).getId()).isEqualTo(USER_ID);
    }

    @Test
    void rejectsATokenIssuedBeforeThePasswordWasChanged() throws Exception {
        // tokenVersion del usuario (2) ya no coincide con el del token (0): se emitió
        // antes de un cambio de contraseña que invalida todas las sesiones anteriores.
        when(jwtService.extractUserId("stale-token")).thenReturn(USER_ID);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(principal(2));
        when(jwtService.extractTokenVersion("stale-token")).thenReturn(0);

        filter.doFilter(requestWithBearer("stale-token"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void clearsTheContextWhenTheTokenCannotBeParsed() throws Exception {
        when(jwtService.extractUserId("garbage")).thenThrow(new RuntimeException("malformed"));

        filter.doFilter(requestWithBearer("garbage"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNotOverrideAnAuthenticationAlreadyPresentInTheContext() throws Exception {
        Authentication existing = new TestingAuthenticationToken("someone-else", "n/a");
        SecurityContextHolder.getContext().setAuthentication(existing);

        // No se debería ni llegar a tocar jwtService: el filtro corta antes por el "if".
        filter.doFilter(requestWithBearer("good-token"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
