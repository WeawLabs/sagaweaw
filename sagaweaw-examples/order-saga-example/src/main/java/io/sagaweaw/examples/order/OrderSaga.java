package io.sagaweaw.examples.order;

import io.sagaweaw.core.RetryPolicy;
import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.StepOutput;
import io.sagaweaw.examples.order.service.InventoryService;
import io.sagaweaw.examples.order.service.PaymentService;
import io.sagaweaw.examples.order.service.ShippingService;
import io.sagaweaw.spring.annotation.Saga;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Saga(name = "order-processing")
@RequiredArgsConstructor
public class OrderSaga implements SagaDefinition<OrderContext> {

    private final InventoryService inventoryService;
    private final PaymentService   paymentService;
    private final ShippingService  shippingService;

    @Override
    public SagaFlow<OrderContext> define(SagaBuilder<OrderContext> saga) {
        return saga
                .step("reserve-inventory")
                .invoke(ctx -> { inventoryService.reserve(ctx.itemId(), ctx.quantity()); })
                .compensate(ctx -> inventoryService.release(ctx.itemId(), ctx.quantity()))

                .step("charge-payment")
                .invoke(ctx -> {
                    String chargeId = paymentService.charge(ctx.customerId(), ctx.amount());
                    return StepOutput.of("chargeId", chargeId);
                })
                .compensate((ctx, output) -> paymentService.refund(output.require("chargeId", String.class)))
                .retryPolicy(RetryPolicy.exponential(3, Duration.ofSeconds(5)))
                .timeout(Duration.ofSeconds(30))

                .step("create-shipment")
                .invoke(ctx -> { shippingService.schedule(ctx.orderId(), ctx.itemId()); })
                .compensate(ctx -> shippingService.cancel(ctx.orderId()))

                .build();
    }
}
