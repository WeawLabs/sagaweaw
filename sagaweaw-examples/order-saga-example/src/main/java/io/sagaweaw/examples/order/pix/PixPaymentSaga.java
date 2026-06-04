package io.sagaweaw.examples.order.pix;

import io.sagaweaw.core.RetryPolicy;
import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.SagaSampler;
import io.sagaweaw.spring.annotation.Saga;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Saga(name = "pix-payment")
@Component
@Slf4j
public class PixPaymentSaga implements SagaDefinition<PixContext>, SagaSampler<PixContext> {

    @Override
    public PixContext sampleContext() {
        return new PixContext(UUID.randomUUID().toString(),
                "joao@pix.example.com", new BigDecimal("250.00"),
                "payer-demo", null, null);
    }

    @Override
    public SagaFlow<PixContext> define(SagaBuilder<PixContext> saga) {
        return saga
                .step("validate-pix-key")
                .invoke(ctx -> {
                    if (ctx.pixKey().startsWith("INVALID-")) {
                        throw new RuntimeException("PIX key not found: " + ctx.pixKey());
                    }
                    log.info("PIX key validated: {}", ctx.pixKey());
                })
                .compensate(ctx -> log.info("Releasing PIX key validation: {}", ctx.pixKey()))

                .step("block-balance")
                .invoke(ctx -> {
                    if (ctx.payerId().startsWith("BLOCK-FAIL-")) {
                        throw new RuntimeException("Insufficient balance for payer: " + ctx.payerId());
                    }
                    log.info("Balance blocked R${} for {}", ctx.amount(), ctx.payerId());
                })
                .compensate(ctx -> log.info("Unblocking balance for {}", ctx.payerId()))
                .retryPolicy(RetryPolicy.exponential(3, Duration.ofMillis(500)))

                .step("transmit-to-bacen")
                .invoke(ctx -> {
                    if (ctx.transactionId().startsWith("BACEN-FAIL-")) {
                        throw new RuntimeException("BACEN transmission timeout for: " + ctx.transactionId());
                    }
                    log.info("Transmitted to BACEN: {}", ctx.transactionId());
                })

                .build();
    }
}
