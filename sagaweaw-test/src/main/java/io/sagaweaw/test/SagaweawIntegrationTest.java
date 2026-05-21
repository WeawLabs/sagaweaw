package io.sagaweaw.test;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import io.sagaweaw.spring.SagaManager;

/**
 * Base class for Sagaweaw integration tests.
 *
 * Starts real PostgreSQL and Kafka containers once per test suite
 * (static containers reused across all test methods — faster builds).
 *
 * Resets SagaTestKit before each test to ensure isolation.
 *
 * Extend this and inject what you need:
 * <pre>
 * class OrderSagaTest extends SagaweawIntegrationTest {
 *
 *     {@literal @}Autowired OrderSaga orderSaga;
 *
 *     {@literal @}Test
 *     void shouldCompensateWhenPaymentFails() {
 *         testKit.simulateFailureOn("charge-payment");
 *
 *         SagaExecution exec = sagaManager.start(
 *             OrderSaga.class, new OrderContext("order-1"));
 *
 *         testKit.assertSaga(exec.getSagaId())
 *             .withinSeconds(10)
 *             .isCompensated()
 *             .hasCompensatedStep("reserve-inventory");
 *     }
 * }
 * </pre>
 */
@SpringBootTest
@Testcontainers
public abstract class SagaweawIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("sagaweaw_test")
                    .withUsername("sagaweaw")
                    .withPassword("sagaweaw");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withKraft();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected SagaTestKit testKit;

    @Autowired
    protected SagaManager sagaManager;

    @BeforeEach
    void resetKit() {
        testKit.reset();
    }
}
