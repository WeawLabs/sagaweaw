package io.sagaweaw.examples.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShippingService {

    public void schedule(String orderId, String itemId) {
        log.info("Scheduling shipment for order {} item {}", orderId, itemId);
    }

    public void cancel(String orderId) {
        log.info("Cancelling shipment for order {}", orderId);
    }
}
