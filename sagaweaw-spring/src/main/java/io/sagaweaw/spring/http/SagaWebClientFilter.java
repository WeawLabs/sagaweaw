package io.sagaweaw.spring.http;

import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Propagates the current saga ID as an X-Saga-ID header on outgoing WebClient calls.
 * MDC is read synchronously at filter assembly time (before Mono subscription),
 * so it captures the saga ID from the calling thread's context.
 */
public class SagaWebClientFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String sagaId = MDC.get("sagaId");
        if (sagaId == null) {
            return next.exchange(request);
        }
        ClientRequest mutated = ClientRequest.from(request)
                .header(SagaRestTemplateInterceptor.HEADER, sagaId)
                .build();
        return next.exchange(mutated);
    }
}
