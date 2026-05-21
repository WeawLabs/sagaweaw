package io.sagaweaw.spring.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Server-side complement to SagaRestTemplateInterceptor.
 * Reads X-Saga-ID from incoming requests and puts it in MDC,
 * so logs on this service are automatically correlated with the calling saga.
 */
public class SagaHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String sagaId = request.getHeader(SagaRestTemplateInterceptor.HEADER);
        if (sagaId != null) {
            MDC.put("sagaId", sagaId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (sagaId != null) {
                MDC.remove("sagaId");
            }
        }
    }
}
