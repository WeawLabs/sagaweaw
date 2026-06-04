package io.sagaweaw.examples.order.pix;

import io.sagaweaw.core.SagaContext;
import java.math.BigDecimal;
import java.util.Optional;

public record PixContext(
        String     transactionId,
        String     pixKey,
        BigDecimal amount,
        String     payerId,
        String     validationToken,
        String     blockId
) implements SagaContext {
    @Override
    public Optional<String> businessKey() {
        return Optional.of("PIX-" + transactionId);
    }
}
