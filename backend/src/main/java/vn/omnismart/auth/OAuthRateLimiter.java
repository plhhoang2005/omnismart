package vn.omnismart.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OAuthRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxRequests;
    private final Duration windowDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

    @Autowired
    public OAuthRateLimiter(
            @Value("${omnismart.security.oauth-rate-limit.max-requests:10}") int maxRequests,
            @Value("${omnismart.security.oauth-rate-limit.window:PT1M}") Duration windowDuration) {
        this(maxRequests, windowDuration, Clock.systemUTC());
    }

    OAuthRateLimiter(int maxRequests, Duration windowDuration, Clock clock) {
        if (maxRequests < 1 || windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("OAuth rate limit must use positive values");
        }
        this.maxRequests = maxRequests;
        this.windowDuration = windowDuration;
        this.clock = clock;
    }

    public Decision tryAcquire(String clientKey) {
        Instant now = clock.instant();
        String trackingKey = boundedTrackingKey(clientKey, now);
        AtomicBoolean allowed = new AtomicBoolean();
        RequestWindow current = windows.compute(trackingKey, (key, existing) -> {
            if (existing == null || !now.isBefore(existing.expiresAt())) {
                allowed.set(true);
                return new RequestWindow(1, now.plus(windowDuration));
            }
            if (existing.requestCount() >= maxRequests) {
                return existing;
            }
            allowed.set(true);
            return new RequestWindow(existing.requestCount() + 1, existing.expiresAt());
        });

        long retryAfterSeconds = Math.max(1, Duration.between(now, current.expiresAt()).toSeconds());
        return new Decision(allowed.get(), retryAfterSeconds);
    }

    private String boundedTrackingKey(String clientKey, Instant now) {
        if (windows.containsKey(clientKey) || windows.size() < MAX_TRACKED_CLIENTS) {
            return clientKey;
        }
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        return windows.size() < MAX_TRACKED_CLIENTS ? clientKey : "__overflow__";
    }

    private record RequestWindow(int requestCount, Instant expiresAt) {
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
