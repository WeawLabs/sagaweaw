package io.sagaweaw.examples.order.subscription;

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

@Saga(name = "subscription-activation")
@Component
@Slf4j
public class SubscriptionActivationSaga implements SagaDefinition<SubscriptionContext>, SagaSampler<SubscriptionContext> {

    @Override
    public SubscriptionContext sampleContext() {
        return new SubscriptionContext(UUID.randomUUID().toString(),
                "plan-pro", new BigDecimal("149.00"), "WELCOME10");
    }

    @Override
    public SagaFlow<SubscriptionContext> define(SagaBuilder<SubscriptionContext> saga) {
        return saga
                .step("charge-subscription")
                .invoke(ctx -> {
                    if (ctx.userId().startsWith("CARD-FAIL-")) {
                        throw new RuntimeException("Card declined for user: " + ctx.userId());
                    }
                    log.info("Charged R${}/month for plan {} user {}", ctx.priceMonthly(), ctx.planId(), ctx.userId());
                })
                .compensate(ctx -> log.info("Refunding subscription charge for {}", ctx.userId()))
                .retryPolicy(RetryPolicy.exponential(3, Duration.ofMillis(500)))

                .step("activate-plan")
                .invoke(ctx -> {
                    if (ctx.planId().startsWith("PLAN-FAIL-")) {
                        throw new RuntimeException("Plan activation service unavailable: " + ctx.planId());
                    }
                    log.info("Plan {} activated for {}", ctx.planId(), ctx.userId());
                })
                .compensate(ctx -> log.info("Deactivating plan {} for {}", ctx.planId(), ctx.userId()))

                .step("send-welcome-email")
                .invoke(ctx -> { log.info("Welcome email sent to user {}", ctx.userId()); })

                .build();
    }
}
