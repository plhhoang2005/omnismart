package vn.omnismart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class OAuthRateLimiterTests {

    @Test
    void rejectsRequestsBeyondLimitAndAllowsThemAfterWindowExpires() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-08-20T00:00:00Z");
        when(clock.instant()).thenReturn(start, start, start, start.plusSeconds(61));
        OAuthRateLimiter limiter = new OAuthRateLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        OAuthRateLimiter.Decision rejected = limiter.tryAcquire("client-a");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60);
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
    }

    @Test
    void keepsIndependentLimitsForDifferentOAuthEndpoints() {
        OAuthRateLimiter limiter = new OAuthRateLimiter(
                1,
                Duration.ofMinutes(1),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire("client|login").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client|callback").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client|login").allowed()).isFalse();
    }
}
