package io.sagaweaw.spring.api;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityTokenInterceptorTest {

    private static final String TOKEN          = "my-secret-token";
    private static final String PREVIOUS_TOKEN = "old-secret-token";

    // Rate limiting disabled, no previous token — preserves original test semantics
    private static ObservabilityTokenInterceptor noRateLimit(String token) {
        return new ObservabilityTokenInterceptor(token, null, 0, 0);
    }

    @Nested
    class WhenTokenNotConfigured {

        @Test
        void returns403WhenNull() throws Exception {
            var response = new MockHttpServletResponse();
            boolean result = noRateLimit(null).preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void returns403WhenBlank() throws Exception {
            var response = new MockHttpServletResponse();
            boolean result = noRateLimit("   ").preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Nested
    class WhenTokenConfigured {

        private final ObservabilityTokenInterceptor interceptor = noRateLimit(TOKEN);

        @Test
        void allowsBearerHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void allowsXSagaweawTokenHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void rejectsWrongToken() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer wrong-token");
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void rejectsNoHeader() throws Exception {
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void rejectsMalformedBearerNoSpace() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer");
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Nested
    class TokenRotation {

        private final ObservabilityTokenInterceptor interceptor =
                new ObservabilityTokenInterceptor(TOKEN, PREVIOUS_TOKEN, 0, 0);

        @Test
        void allowsCurrentToken() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void allowsPreviousTokenDuringGracePeriod() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", PREVIOUS_TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void previousTokenViaBearerHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + PREVIOUS_TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void rejectsUnknownTokenEvenWhenPreviousIsSet() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", "totally-unknown-token");
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void previousTokenNullMeansNoPreviousToken() throws Exception {
            var interceptorNoPrev = new ObservabilityTokenInterceptor(TOKEN, null, 0, 0);
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", PREVIOUS_TOKEN);
            var response = new MockHttpServletResponse();
            assertThat(interceptorNoPrev.preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void previousTokenBlankMeansNoPreviousToken() throws Exception {
            var interceptorBlankPrev = new ObservabilityTokenInterceptor(TOKEN, "   ", 0, 0);
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", PREVIOUS_TOKEN);
            var response = new MockHttpServletResponse();
            assertThat(interceptorBlankPrev.preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Nested
    class RateLimiting {

        private ObservabilityTokenInterceptor interceptor() {
            return new ObservabilityTokenInterceptor(TOKEN, null, 3, 5);
        }

        @Test
        void allowsAuthBeforeThreshold() throws Exception {
            var interceptor = interceptor();
            failTimes(interceptor, 2, "127.0.0.1");

            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void returns429AfterMaxAttempts() throws Exception {
            var interceptor = interceptor();
            failTimes(interceptor, 3, "127.0.0.1");

            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(requestFrom("127.0.0.1"), response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isNotNull();
        }

        @Test
        void retryAfterHeaderIsPositive() throws Exception {
            var interceptor = interceptor();
            failTimes(interceptor, 3, "10.0.0.1");

            var response = new MockHttpServletResponse();
            interceptor.preHandle(requestFrom("10.0.0.1"), response, new Object());

            long retryAfter = Long.parseLong(response.getHeader("Retry-After"));
            assertThat(retryAfter).isGreaterThan(0);
        }

        @Test
        void successfulAuthResetsCounter() throws Exception {
            var interceptor = interceptor();
            failTimes(interceptor, 2, "127.0.0.1");

            var successRequest = new MockHttpServletRequest();
            successRequest.setRemoteAddr("127.0.0.1");
            successRequest.addHeader("X-Sagaweaw-Token", TOKEN);
            interceptor.preHandle(successRequest, new MockHttpServletResponse(), new Object());

            // 2 more failures after reset — should NOT be locked yet
            failTimes(interceptor, 2, "127.0.0.1");
            var response = new MockHttpServletResponse();
            interceptor.preHandle(requestFrom("127.0.0.1"), response, new Object());
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void differentIpsHaveIndependentCounters() throws Exception {
            var interceptor = interceptor();
            failTimes(interceptor, 3, "192.168.1.1");

            var request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.2");
            request.addHeader("X-Sagaweaw-Token", TOKEN);
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void usesXForwardedForOverRemoteAddr() throws Exception {
            var interceptor = interceptor();
            for (int i = 0; i < 3; i++) {
                var req = new MockHttpServletRequest();
                req.setRemoteAddr("10.0.0.1");
                req.addHeader("X-Forwarded-For", "203.0.113.1");
                req.addHeader("X-Sagaweaw-Token", "wrong");
                interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
            }

            var req = new MockHttpServletRequest();
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.1");
            var response = new MockHttpServletResponse();
            interceptor.preHandle(req, response, new Object());
            assertThat(response.getStatus()).isEqualTo(429);

            var req2 = new MockHttpServletRequest();
            req2.setRemoteAddr("10.0.0.1");
            req2.addHeader("X-Forwarded-For", "203.0.113.99");
            req2.addHeader("X-Sagaweaw-Token", TOKEN);
            assertThat(interceptor.preHandle(req2, new MockHttpServletResponse(), new Object())).isTrue();
        }

        @Test
        void xForwardedForWithMultipleProxiesTakesFirst() throws Exception {
            var interceptor = interceptor();
            failTimes(interceptor, 3, "203.0.113.5, 10.0.0.1, 10.0.0.2");

            var req = new MockHttpServletRequest();
            req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 10.0.0.2");
            var response = new MockHttpServletResponse();
            interceptor.preHandle(req, response, new Object());
            assertThat(response.getStatus()).isEqualTo(429);
        }

        @Test
        void disabledWhenMaxAttemptsIsZero() throws Exception {
            var interceptor = new ObservabilityTokenInterceptor(TOKEN, null, 0, 0);
            for (int i = 0; i < 100; i++) {
                var req = new MockHttpServletRequest();
                req.addHeader("X-Sagaweaw-Token", "wrong");
                var response = new MockHttpServletResponse();
                interceptor.preHandle(req, response, new Object());
                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
            }
        }

        private void failTimes(ObservabilityTokenInterceptor interceptor, int times, String ip) throws Exception {
            for (int i = 0; i < times; i++) {
                var req = new MockHttpServletRequest();
                if (ip.contains(",")) {
                    req.addHeader("X-Forwarded-For", ip);
                } else {
                    req.setRemoteAddr(ip);
                }
                req.addHeader("X-Sagaweaw-Token", "wrong-token");
                interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
            }
        }

        private MockHttpServletRequest requestFrom(String ip) {
            var req = new MockHttpServletRequest();
            req.setRemoteAddr(ip);
            req.addHeader("X-Sagaweaw-Token", "wrong-token");
            return req;
        }
    }
}
