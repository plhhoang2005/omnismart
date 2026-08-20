package vn.omnismart.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuthRateLimitFilterTests {

    @Test
    void returns429WithRetryAfterForRepeatedLoginAttempts() throws Exception {
        OAuthRateLimiter limiter = new OAuthRateLimiter(
                1,
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        OAuthRateLimitFilter filter = new OAuthRateLimitFilter(limiter);

        MockHttpServletRequest firstRequest = loginRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, new MockFilterChain());

        MockHttpServletRequest secondRequest = loginRequest();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, new MockFilterChain());

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(secondResponse.getContentAsString()).contains("OAUTH_RATE_LIMITED");
    }

    @Test
    void doesNotRateLimitUnrelatedApiRequests() throws Exception {
        OAuthRateLimiter limiter = new OAuthRateLimiter(
                1,
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        OAuthRateLimitFilter filter = new OAuthRateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stores");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/oauth2/authorization/google");
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
