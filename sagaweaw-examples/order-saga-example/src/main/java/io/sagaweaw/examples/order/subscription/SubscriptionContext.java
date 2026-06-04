package io.sagaweaw.examples.order.subscription;

import io.sagaweaw.core.SagaContext;
import java.math.BigDecimal;
import java.util.Optional;

public record SubscriptionContext(
        String     userId,
        String     planId,
        BigDecimal priceMonthly,
        String     promoCode
) implements SagaContext {
    @Override
    public Optional<String> businessKey() {
        return Optional.of("SUB-" + userId + "-" + planId);
    }
}
