package io.sagaweaw.examples.pix.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class NotificationService {

    public void notifyBoth(String originAccountId, String destinationKey, BigDecimal amount) {
        log.info("Notifying origin {} and destination {} of transfer {}", originAccountId, destinationKey, amount);
    }

    public void sendReceipt(String transactionId) {
        log.info("Sending receipt for transaction {}", transactionId);
    }

    public void notifyFailure(String accountId, String failedStep) {
        log.info("Notifying failure on step {} to account {}", failedStep, accountId);
    }
}
