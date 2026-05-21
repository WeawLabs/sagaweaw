package io.sagaweaw.spring.http;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propagates the current saga ID as an X-Saga-ID header on outgoing HTTP calls.
 * Works for both RestTemplate and RestClient — both use ClientHttpRequestInterceptor.
 * The saga ID is available in MDC during step execution (set by StepExecutor).
 */
public class SagaRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Saga-ID";

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String sagaId = MDC.get("sagaId");
        if (sagaId != null) {
            request.getHeaders().set(HEADER, sagaId);
        }
        return execution.execute(request, body);
    }
}
