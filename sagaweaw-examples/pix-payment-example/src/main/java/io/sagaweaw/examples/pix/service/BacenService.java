package io.sagaweaw.examples.pix.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class BacenService {

    public String resolveDict(String key) {
        log.info("Resolving DICT key {}", key);
        return "account-" + key;
    }

    public void transmit(String transactionId, BigDecimal amount) {
        log.info("Transmitting transaction {} of {} to BACEN", transactionId, amount);
    }
}
