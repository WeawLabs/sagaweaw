package io.sagaweaw.examples.order;

import io.sagaweaw.core.SagaContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public record OrderContext(
        String     orderId,
        String     customerId,
        String     itemId,
        int        quantity,
        BigDecimal amount
) implements SagaContext {

    @Override
    public Optional<String> businessKey() {
        return Optional.of("ORDER " + orderId);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of("customer", customerId, "item", itemId);
    }
}
