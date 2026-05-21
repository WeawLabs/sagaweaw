package io.sagaweaw.examples.pix;

import io.sagaweaw.core.RetryPolicy;
import io.sagaweaw.core.SagaBuilder;
import io.sagaweaw.core.SagaDefinition;
import io.sagaweaw.core.SagaFlow;
import io.sagaweaw.core.StepOutput;
import io.sagaweaw.examples.pix.service.AccountService;
import io.sagaweaw.examples.pix.service.BacenService;
import io.sagaweaw.examples.pix.service.NotificationService;
import io.sagaweaw.spring.annotation.Saga;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Saga(name = "pix-payment")
@RequiredArgsConstructor
public class PixPaymentSaga implements SagaDefinition<PixContext> {

    private final AccountService      accountService;
    private final BacenService        bacenService;
    private final NotificationService notificationService;

    @Override
    public SagaFlow<PixContext> define(SagaBuilder<PixContext> saga) {
        return saga
                .step("block-balance")
                .invoke(ctx -> { accountService.block(ctx.originAccountId(), ctx.amount()); })
                .compensate(ctx -> accountService.unblock(ctx.originAccountId(), ctx.amount()))

                .step("validate-dict")
                .invoke(ctx -> {
                    String accountId = bacenService.resolveDict(ctx.destinationKey());
                    return StepOutput.of("destinationAccountId", accountId);
                })
                .compensate(ctx -> {})
                .timeout(Duration.ofSeconds(5))

                // PIVOT — no compensate, point of no return
                .step("transmit-to-bacen")
                .invoke(ctx -> { bacenService.transmit(ctx.transactionId(), ctx.amount()); })
                .timeout(Duration.ofSeconds(10))

                // RETRIABLE — must always succeed after the pivot; re-resolves DICT (idempotent)
                .step("credit-destination")
                .invoke(ctx -> {
                    String accountId = bacenService.resolveDict(ctx.destinationKey());
                    accountService.credit(accountId, ctx.amount());
                    return StepOutput.EMPTY;
                })
                .retryPolicy(RetryPolicy.infinite(Duration.ofSeconds(5)))

                .step("notify-parties")
                .invoke(ctx -> {
                    notificationService.notifyBoth(
                            ctx.originAccountId(), ctx.destinationKey(), ctx.amount());
                })
                .retryPolicy(RetryPolicy.infinite(Duration.ofSeconds(3)))

                .onSuccess(ctx ->
                        notificationService.sendReceipt(ctx.transactionId()))

                .onFailure((ctx, failedStep, error) ->
                        notificationService.notifyFailure(ctx.originAccountId(), failedStep))

                .build();
    }
}
