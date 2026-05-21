package io.sagaweaw.examples.pix;

import io.sagaweaw.core.SagaContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public record PixContext(
        String     transactionId,
        String     originAccountId,
        String     destinationKey,
        BigDecimal amount
) implements SagaContext {

    @Override
    public Optional<String> businessKey() {
        return Optional.of("PIX " + transactionId);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of("origin", originAccountId, "destination", destinationKey);
    }
}
