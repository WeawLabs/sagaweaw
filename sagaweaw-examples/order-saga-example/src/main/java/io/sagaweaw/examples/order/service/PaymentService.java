package io.sagaweaw.examples.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    public String charge(String customerId, BigDecimal amount) {
        if (customerId.startsWith("FAIL-PAY-")) {
            throw new RuntimeException("Payment gateway rejected customer: " + customerId);
        }
        String chargeId = UUID.randomUUID().toString();
        log.info("Charging {} to customer {} — chargeId: {}", amount, customerId, chargeId);
        return chargeId;
    }

    public void refund(String chargeId) {
        log.info("Refunding charge {}", chargeId);
    }
}
