package io.sagaweaw.spring.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// 403 by default — endpoints are only accessible when sagaweaw.observability.token is set.
public class ObservabilityTokenInterceptor implements HandlerInterceptor {

    private final String expectedToken;

    public ObservabilityTokenInterceptor(String expectedToken) {
        this.expectedToken = expectedToken;
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

        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")
                && constantTimeEquals(expectedToken, bearer.substring(7))) {
            return true;
        }

        String direct = request.getHeader("X-Sagaweaw-Token");
        if (direct != null && constantTimeEquals(expectedToken, direct)) {
            return true;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Sagaweaw observability token.");
        return false;
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
