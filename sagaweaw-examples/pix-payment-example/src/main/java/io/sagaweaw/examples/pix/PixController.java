package io.sagaweaw.examples.pix;

import io.sagaweaw.spring.SagaManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/pix")
@RequiredArgsConstructor
public class PixController {

    private final SagaManager sagaManager;

    @PostMapping
    public Map<String, String> send(@RequestBody PixRequest request) {
        var context = new PixContext(
                UUID.randomUUID().toString(),
                request.originAccountId(),
                request.destinationKey(),
                request.amount()
        );
        var execution = sagaManager.start(PixPaymentSaga.class, context);
        return Map.of("sagaId", execution.sagaId());
    }

    record PixRequest(
            String originAccountId,
            String destinationKey,
            BigDecimal amount
    ) {}
}
