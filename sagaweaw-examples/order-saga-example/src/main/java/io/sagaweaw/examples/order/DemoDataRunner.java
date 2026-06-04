package io.sagaweaw.examples.order;

import io.sagaweaw.examples.order.pix.PixContext;
import io.sagaweaw.examples.order.pix.PixPaymentSaga;
import io.sagaweaw.examples.order.subscription.SubscriptionActivationSaga;
import io.sagaweaw.examples.order.subscription.SubscriptionContext;
import io.sagaweaw.spring.SagaManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "sagaweaw.auto-start.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataRunner implements ApplicationRunner {

    private final SagaManager sagaManager;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("--- DemoDataRunner: seeding dashboard demo data ---");

        // ORDER-PROCESSING — 2 completed, 3 compensated, 2 dead letters
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-1", "ITEM-LAPTOP-PRO",  1, bd("2499.00"))); pause();
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-2", "ITEM-HEADPHONES",  2, bd("349.00")));  pause();
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-3", "FAIL-SHIP-ITEM-A", 1, bd("99.90")));  pause();
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-4", "FAIL-SHIP-ITEM-B", 1, bd("149.00"))); pause();
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-5", "FAIL-SHIP-ITEM-C", 3, bd("59.90")));  pause();
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-6", "FAIL-COMP-ITEM-1", 1, bd("299.00"))); pause();
        sagaManager.start(OrderSaga.class, new OrderContext(uid(), "customer-7", "FAIL-COMP-ITEM-2", 1, bd("189.00"))); pause();

        // PIX-PAYMENT — 2 completed, 5 compensated
        sagaManager.start(PixPaymentSaga.class, new PixContext(uid(),               "joao@pix.example.com", bd("250.00"), "payer-1", null, null)); pause();
        sagaManager.start(PixPaymentSaga.class, new PixContext(uid(),               "maria@banco.com.br",   bd("75.50"),  "payer-2", null, null)); pause();
        sagaManager.start(PixPaymentSaga.class, new PixContext("BACEN-FAIL-"+uid(), "valid@pix.com",        bd("500.00"), "payer-3", null, null)); pause();
        sagaManager.start(PixPaymentSaga.class, new PixContext("BACEN-FAIL-"+uid(), "valid2@pix.com",       bd("120.00"), "payer-4", null, null)); pause();
        sagaManager.start(PixPaymentSaga.class, new PixContext("BACEN-FAIL-"+uid(), "valid3@pix.com",       bd("30.00"),  "payer-5", null, null)); pause();
        sagaManager.start(PixPaymentSaga.class, new PixContext(uid(),               "INVALID-key-1",        bd("88.00"),  "payer-6", null, null)); pause();
        sagaManager.start(PixPaymentSaga.class, new PixContext(uid(),               "INVALID-key-2",        bd("44.00"),  "payer-7", null, null)); pause();

        // SUBSCRIPTION-ACTIVATION — 2 completed, 3 compensated, 1 dead letter
        sagaManager.start(SubscriptionActivationSaga.class, new SubscriptionContext(uid(),              "plan-pro",             bd("149.00"), "WELCOME10")); pause();
        sagaManager.start(SubscriptionActivationSaga.class, new SubscriptionContext(uid(),              "plan-basic",           bd("49.00"),  null));        pause();
        sagaManager.start(SubscriptionActivationSaga.class, new SubscriptionContext("CARD-FAIL-"+uid(), "plan-pro",             bd("149.00"), null));        pause();
        sagaManager.start(SubscriptionActivationSaga.class, new SubscriptionContext("CARD-FAIL-"+uid(), "plan-pro",             bd("149.00"), null));        pause();
        sagaManager.start(SubscriptionActivationSaga.class, new SubscriptionContext("CARD-FAIL-"+uid(), "plan-basic",           bd("49.00"),  null));        pause();
        sagaManager.start(SubscriptionActivationSaga.class, new SubscriptionContext("CARD-FAIL-"+uid(), "PLAN-FAIL-enterprise", bd("349.00"), null));        pause();

        log.info("--- DemoDataRunner: 21 sagas fired across 3 types ---");
    }

    private static void pause() throws InterruptedException { Thread.sleep(100); }
    private static String uid()              { return UUID.randomUUID().toString(); }
    private static BigDecimal bd(String val) { return new BigDecimal(val); }
}
