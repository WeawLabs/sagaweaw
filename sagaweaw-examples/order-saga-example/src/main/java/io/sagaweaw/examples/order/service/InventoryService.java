package io.sagaweaw.examples.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InventoryService {

    public void reserve(String itemId, int quantity) {
        log.info("Reserving {} units of item {}", quantity, itemId);
    }

    public void release(String itemId, int quantity) {
        log.info("Releasing {} units of item {}", quantity, itemId);
    }
}
