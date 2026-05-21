package io.sagaweaw.spring.api;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityTokenInterceptorTest {

    private static final String TOKEN = "my-secret-token";

    @Nested
    class WhenTokenNotConfigured {

        private final ObservabilityTokenInterceptor interceptor =
                new ObservabilityTokenInterceptor(null);

        @Test
        void returns403() throws Exception {
            var response = new MockHttpServletResponse();
            boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void returns403WhenBlankToken() throws Exception {
            var interceptorBlank = new ObservabilityTokenInterceptor("   ");
            var response = new MockHttpServletResponse();
            boolean result = interceptorBlank.preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    @Nested
    class WhenTokenConfigured {

        private final ObservabilityTokenInterceptor interceptor =
                new ObservabilityTokenInterceptor(TOKEN);

        @Test
        void allowsBearerHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + TOKEN);
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        void allowsXSagaweawTokenHeader() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("X-Sagaweaw-Token", TOKEN);
            var response = new MockHttpServletResponse();
            assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        }

        @Test
        void rejectsWrongToken() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer wrong-token");
            var response = new MockHttpServletResponse();
            boolean result = interceptor.preHandle(request, response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void rejectsNoHeader() throws Exception {
            var response = new MockHttpServletResponse();
            boolean result = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        void rejectsMalformedBearerNoSpace() throws Exception {
            var request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer");
            var response = new MockHttpServletResponse();
            boolean result = interceptor.preHandle(request, response, new Object());
            assertThat(result).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
