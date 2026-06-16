package io.sagaweaw.spring.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

// 403 by default — endpoints are only accessible when sagaweaw.observability.token is set.
// Rate limiting: after maxAttempts consecutive failures from the same IP, that IP is locked out
// for lockoutMinutes. Counter resets on successful auth. Lockout is in-memory (resets on restart).
// Token rotation: if previousToken is set, it is accepted alongside the current token so that
// clients can be migrated without downtime. A WARN is logged every time previousToken is used.
public class ObservabilityTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityTokenInterceptor.class);

    private final String expectedToken;
    private final String previousToken;
    private final int maxAttempts;
    private final int lockoutMinutes;
    private final ConcurrentHashMap<String, AttemptRecord> rateLimiter = new ConcurrentHashMap<>();

    public ObservabilityTokenInterceptor(String expectedToken, String previousToken,
                                          int maxAttempts, int lockoutMinutes) {
        this.expectedToken = expectedToken;
        this.previousToken = previousToken;
        this.maxAttempts = maxAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) throws Exception {
        if (expectedToken == null || expectedToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Sagaweaw observability API is locked. "
                    + "Set sagaweaw.observability.token in application.properties to enable access.");
            return false;
        }

        String ip = clientIp(request);

        // Check rate-limit lockout before attempting auth
        if (maxAttempts > 0) {
            AttemptRecord existing = rateLimiter.get(ip);
            if (existing != null && existing.lockedUntil() != null) {
                if (Instant.now().isBefore(existing.lockedUntil())) {
                    long retryAfter = ChronoUnit.SECONDS.between(Instant.now(), existing.lockedUntil());
                    response.setHeader("Retry-After", String.valueOf(Math.max(1L, retryAfter)));
                    response.sendError(429, "Too many failed authentication attempts. Try again later.");
                    return false;
                }
                rateLimiter.remove(ip, existing);
            }
        }

        if (isAuthenticated(request, ip)) {
            rateLimiter.remove(ip);
            return true;
        }

        if (maxAttempts > 0) {
            rateLimiter.compute(ip, (k, r) -> {
                int newCount = (r == null ? 0 : r.count()) + 1;
                Instant lockUntil = newCount >= maxAttempts
                        ? Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES)
                        : null;
                return new AttemptRecord(newCount, lockUntil);
            });
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Sagaweaw observability token.");
        return false;
    }

    private boolean isAuthenticated(HttpServletRequest request, String ip) {
        String provided = extractToken(request);
        if (provided == null) return false;

        if (constantTimeEquals(expectedToken, provided)) return true;

        if (previousToken != null && !previousToken.isBlank()
                && constantTimeEquals(previousToken, provided)) {
            log.warn("[sagaweaw] Dashboard authenticated with previous token from {} — "
                    + "remove sagaweaw.observability.previous-token to complete rotation.", ip);
            return true;
        }

        return false;
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return request.getHeader("X-Sagaweaw-Token");
    }

    // X-Forwarded-For can contain a chain ("client, proxy1, proxy2") — take the leftmost value.
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private record AttemptRecord(int count, Instant lockedUntil) {}
}
