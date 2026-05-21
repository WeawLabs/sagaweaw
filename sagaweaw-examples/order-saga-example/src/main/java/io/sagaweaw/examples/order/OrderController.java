package io.sagaweaw.examples.order;

import io.sagaweaw.spring.SagaManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final SagaManager sagaManager;

    @PostMapping
    public Map<String, String> placeOrder(@RequestBody OrderRequest request) {
        var context = new OrderContext(
                UUID.randomUUID().toString(),
                request.customerId(),
                request.itemId(),
                request.quantity(),
                request.amount()
        );
        var execution = sagaManager.start(OrderSaga.class, context);
        return Map.of("sagaId", execution.sagaId());
    }

    record OrderRequest(
            String customerId,
            String itemId,
            int quantity,
            BigDecimal amount
    ) {}
}
