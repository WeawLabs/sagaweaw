package io.sagaweaw.examples.pix.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class AccountService {

    public void block(String accountId, BigDecimal amount) {
        log.info("Blocking {} from account {}", amount, accountId);
    }

    public void unblock(String accountId, BigDecimal amount) {
        log.info("Unblocking {} on account {}", amount, accountId);
    }

    public void credit(String accountId, BigDecimal amount) {
        log.info("Crediting {} to account {}", amount, accountId);
    }
}
