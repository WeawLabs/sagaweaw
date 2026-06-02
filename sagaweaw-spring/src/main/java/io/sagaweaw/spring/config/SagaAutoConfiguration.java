package io.sagaweaw.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sagaweaw.spring.alert.AlertListener;
import io.sagaweaw.spring.alert.AlertWebhookService;
import io.sagaweaw.spring.repository.SagaArchiveRepository;
import io.sagaweaw.spring.scheduler.RetentionScheduler;
import org.springframework.context.annotation.Import;
import io.sagaweaw.spring.SagaManager;
import io.sagaweaw.spring.health.SagaHealthIndicator;
import io.sagaweaw.spring.api.DeadLetterController;
import io.sagaweaw.spring.api.GrafanaDashboardController;
import io.sagaweaw.spring.api.ObservabilityTokenInterceptor;
import io.sagaweaw.spring.api.SagaObservabilityController;
import io.sagaweaw.spring.api.SagaTriggerController;
import io.sagaweaw.spring.api.SagaWebSocketHandler;
import io.sagaweaw.spring.engine.CompensationExecutor;
import io.sagaweaw.spring.engine.SagaAutoStartRunner;
import io.sagaweaw.spring.engine.SagaRegistry;
import io.sagaweaw.spring.engine.SpringSagaEngine;
import io.sagaweaw.spring.engine.StepExecutor;
import io.sagaweaw.spring.http.SagaHeaderFilter;
import io.sagaweaw.spring.http.SagaRestTemplateInterceptor;
import io.sagaweaw.spring.http.SagaWebClientFilter;
import io.sagaweaw.spring.interceptor.MetricsInterceptor;
import io.sagaweaw.spring.interceptor.SagaLifecycleMetrics;
import io.sagaweaw.spring.interceptor.SagaStepInterceptor;
import io.sagaweaw.spring.interceptor.TracingInterceptor;
import io.sagaweaw.spring.mapper.SagaMapper;
import io.sagaweaw.spring.repository.DeadLetterRepository;
import io.sagaweaw.spring.repository.OutboxMessageRepository;
import io.sagaweaw.spring.repository.SagaEventRepository;
import io.sagaweaw.spring.repository.SagaRepository;
import io.sagaweaw.spring.repository.SagaStepRepository;
import io.sagaweaw.spring.scheduler.OutboxRelay;
import io.sagaweaw.spring.scheduler.RetryScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.io.IOException;
import java.util.List;

@AutoConfiguration(afterName = {
        // Spring Boot 3.x names (kept for backwards compatibility)
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        // kept for reference only — sagaweaw targets Spring Boot 3.x
})
@EnableScheduling
@EnableConfigurationProperties(SagaProperties.class)
@Import(SagaJacksonModule.class)
@ConditionalOnProperty(prefix = "sagaweaw", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SagaRegistry sagaRegistry(ApplicationContext applicationContext) {
        return new SagaRegistry(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaMapper sagaMapper(ObjectMapper objectMapper) {
        return new SagaMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public StepExecutor stepExecutor(SagaStepRepository stepRepository,
                                     OutboxMessageRepository outboxRepository,
                                     SagaMapper mapper,
                                     List<SagaStepInterceptor> interceptors,
                                     org.springframework.beans.factory.ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        return new StepExecutor(stepRepository, outboxRepository, mapper, interceptors,
                observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean
    public CompensationExecutor compensationExecutor(SagaStepRepository stepRepository,
                                                     SagaEventRepository eventRepository,
                                                     StepExecutor stepExecutor,
                                                     ApplicationEventPublisher publisher) {
        return new CompensationExecutor(stepRepository, eventRepository, stepExecutor, publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringSagaEngine sagaEngine(SagaRegistry registry,
                                       SagaRepository sagaRepository,
                                       SagaStepRepository stepRepository,
                                       SagaEventRepository eventRepository,
                                       DeadLetterRepository deadLetterRepository,
                                       StepExecutor stepExecutor,
                                       CompensationExecutor compensationExecutor,
                                       SagaMapper mapper,
                                       ApplicationEventPublisher publisher,
                                       SagaProperties properties) {
        return new SpringSagaEngine(registry, sagaRepository, stepRepository,
                eventRepository, deadLetterRepository, stepExecutor,
                compensationExecutor, mapper, publisher, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SagaManager sagaManager(SagaRegistry registry, SpringSagaEngine engine) {
        return new SagaManager(registry, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.auto-start", name = "enabled", havingValue = "true")
    public SagaAutoStartRunner sagaAutoStartRunner(ApplicationContext applicationContext,
                                                    SagaManager sagaManager) {
        return new SagaAutoStartRunner(applicationContext, sagaManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "sagaweaw.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(OutboxMessageRepository outboxRepository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper) {
        return new OutboxRelay(outboxRepository, kafkaTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryScheduler retryScheduler(SagaStepRepository stepRepository,
                                         SpringSagaEngine engine) {
        return new RetryScheduler(stepRepository, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetentionScheduler retentionScheduler(SagaRepository sagaRepository,
                                                  SagaStepRepository sagaStepRepository,
                                                  SagaEventRepository sagaEventRepository,
                                                  SagaArchiveRepository sagaArchiveRepository,
                                                  SagaProperties properties) {
        return new RetentionScheduler(sagaRepository, sagaStepRepository,
                sagaEventRepository, sagaArchiveRepository, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.alerts", name = "webhook-url")
    public AlertWebhookService alertWebhookService(SagaProperties properties) {
        return new AlertWebhookService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.alerts", name = "webhook-url")
    public AlertListener alertListener(AlertWebhookService alertWebhookService,
                                       SagaRepository sagaRepository,
                                       SagaProperties properties) {
        return new AlertListener(alertWebhookService, sagaRepository, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.observability", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SagaObservabilityController sagaObservabilityController(
            SagaRepository sagaRepository,
            DeadLetterRepository deadLetterRepository,
            SagaEventRepository sagaEventRepository,
            SagaStepRepository sagaStepRepository,
            OutboxMessageRepository outboxMessageRepository,
            SpringSagaEngine engine,
            SagaMapper mapper,
            SagaProperties properties,
            SagaRegistry sagaRegistry) {
        return new SagaObservabilityController(sagaRepository, deadLetterRepository,
                sagaEventRepository, sagaStepRepository, outboxMessageRepository, engine, mapper, properties, sagaRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.observability", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SagaTriggerController sagaTriggerController(
            SagaRegistry sagaRegistry,
            SagaManager sagaManager,
            ObjectMapper objectMapper,
            ApplicationContext applicationContext) {
        return new SagaTriggerController(sagaRegistry, sagaManager, objectMapper, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.observability", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public GrafanaDashboardController grafanaDashboardController() {
        return new GrafanaDashboardController();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sagaweaw.observability", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public DeadLetterController deadLetterController(
            DeadLetterRepository deadLetterRepository,
            SagaRepository sagaRepository,
            SpringSagaEngine engine) {
        return new DeadLetterController(deadLetterRepository, sagaRepository, engine);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "simpMessagingTemplate")
    public SagaWebSocketHandler sagaWebSocketHandler(
            org.springframework.messaging.simp.SimpMessagingTemplate messaging,
            ObjectMapper objectMapper) {
        return new SagaWebSocketHandler(messaging, objectMapper);
    }

    // ------------------------------------------------------------------ Task 33 — embedded dashboard

    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "sagaweaw.dashboard", name = "mode", havingValue = "embedded", matchIfMissing = true)
    static class SagaDashboardEmbeddedConfiguration implements WebMvcConfigurer {

        @org.springframework.beans.factory.annotation.Autowired
        private SagaProperties properties;

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            String base = dashboardPath();
            String location = "classpath:/META-INF/sagaweaw-dashboard/";
            registry.addResourceHandler(base, base + "/**")
                    .addResourceLocations(location)
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver() {
                        @Override
                        protected Resource getResource(String resourcePath, Resource location) throws IOException {
                            if (resourcePath.isEmpty() || resourcePath.equals("/")) {
                                return new ClassPathResource("META-INF/sagaweaw-dashboard/index.html");
                            }
                            Resource resource = location.createRelative(resourcePath);
                            if (resource.exists() && resource.isReadable()) {
                                return resource;
                            }
                            return new ClassPathResource("META-INF/sagaweaw-dashboard/index.html");
                        }
                    });
        }

        private String dashboardPath() {
            if (properties.dashboard() == null) return "/sagaweaw";
            String p = properties.dashboard().path();
            return (p == null || p.isBlank()) ? "/sagaweaw" : p;
        }
    }

    // ------------------------------------------------------------------ Task 24 — X-Saga-ID propagation

    @Configuration
    @ConditionalOnWebApplication
    static class SagaHeaderPropagationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public FilterRegistrationBean<SagaHeaderFilter> sagaHeaderFilter() {
            FilterRegistrationBean<SagaHeaderFilter> reg = new FilterRegistrationBean<>(new SagaHeaderFilter());
            reg.addUrlPatterns("/*");
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            reg.setName("sagaHeaderFilter");
            return reg;
        }

        @Configuration
        @ConditionalOnClass(name = "org.springframework.boot.web.client.RestTemplateCustomizer")
        static class RestTemplateCustomizerConfiguration {
            @Bean
            @ConditionalOnMissingBean(name = "sagaRestTemplateCustomizer")
            public org.springframework.boot.web.client.RestTemplateCustomizer sagaRestTemplateCustomizer() {
                return restTemplate -> restTemplate.getInterceptors().add(new SagaRestTemplateInterceptor());
            }
        }

        @Configuration
        @ConditionalOnClass(name = "org.springframework.boot.web.client.RestClientCustomizer")
        static class RestClientCustomizerConfiguration {
            @Bean
            @ConditionalOnMissingBean(name = "sagaRestClientCustomizer")
            public org.springframework.boot.web.client.RestClientCustomizer sagaRestClientCustomizer() {
                return builder -> builder.requestInterceptor(new SagaRestTemplateInterceptor());
            }
        }

        @Configuration
        @ConditionalOnClass(name = {
            "org.springframework.boot.web.reactive.function.client.WebClientCustomizer",
            "org.springframework.web.reactive.function.client.ExchangeFilterFunction"
        })
        static class WebClientCustomizerConfiguration {
            @Bean
            @ConditionalOnMissingBean(name = "sagaWebClientCustomizer")
            public org.springframework.boot.web.reactive.function.client.WebClientCustomizer sagaWebClientCustomizer() {
                return builder -> builder.filter(new SagaWebClientFilter());
            }
        }
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    static class SagaHealthConfiguration {
        @Bean("sagasHealthIndicator")
        @ConditionalOnMissingBean(name = "sagasHealthIndicator")
        public SagaHealthIndicator sagasHealthIndicator(SagaRepository sagaRepository,
                                                        DeadLetterRepository deadLetterRepository,
                                                        SagaProperties properties) {
            return new SagaHealthIndicator(sagaRepository, deadLetterRepository, properties);
        }
    }

    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class SagaMetricsConfiguration {
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(MeterRegistry.class)
        public MetricsInterceptor metricsInterceptor(MeterRegistry meterRegistry) {
            return new MetricsInterceptor(meterRegistry);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(MeterRegistry.class)
        public SagaLifecycleMetrics sagaLifecycleMetrics(
                MeterRegistry meterRegistry,
                OutboxMessageRepository outboxMessageRepository) {
            return new SagaLifecycleMetrics(meterRegistry, outboxMessageRepository);
        }
    }

    @Configuration
    @ConditionalOnClass(ObservationRegistry.class)
    @ConditionalOnProperty(prefix = "sagaweaw.tracing", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class SagaTracingConfiguration {
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(ObservationRegistry.class)
        public TracingInterceptor tracingInterceptor(ObservationRegistry observationRegistry) {
            return new TracingInterceptor(observationRegistry);
        }
    }

    @Bean
    @ConditionalOnMissingBean(name = "sagaweawCorsConfigurer")
    @ConditionalOnWebApplication
    public WebMvcConfigurer sagaweawCorsConfigurer(SagaProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                SagaProperties.Observability obs = properties.observability();
                if (obs == null || obs.cors() == null || !obs.cors().isConfigured()) return;
                String[] origins = obs.cors().allowedOrigins().toArray(new String[0]);
                registry.addMapping("/api/sagas/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST")
                        .allowedHeaders("Authorization", "X-Sagaweaw-Token", "Content-Type")
                        .maxAge(3600);
                registry.addMapping("/api/dead-letters/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST")
                        .allowedHeaders("Authorization", "X-Sagaweaw-Token", "Content-Type")
                        .maxAge(3600);
            }
        };
    }

    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "sagaweaw.observability", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class SagaObservabilitySecurityConfiguration implements WebMvcConfigurer {

        @org.springframework.beans.factory.annotation.Autowired
        private SagaProperties properties;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            String token = properties.observability() != null
                    ? properties.observability().token()
                    : null;
            registry.addInterceptor(new ObservabilityTokenInterceptor(token))
                    .addPathPatterns("/api/sagas/**", "/api/dead-letters/**", "/api/grafana-dashboard");
        }
    }

    @Configuration
    @ConditionalOnWebApplication
    @ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer")
    @ConditionalOnMissingBean(name = "sagaWebSocketConfig")
    @EnableWebSocketMessageBroker
    static class SagaWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

        @org.springframework.beans.factory.annotation.Autowired
        private SagaProperties properties;

        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            SagaProperties.WebSocket ws = properties != null ? properties.websocket() : null;
            if (ws != null && ws.hasRelay()) {
                if (!ws.hasRelayCredentials()) {
                    throw new IllegalStateException(
                            "sagaweaw.websocket.relay-login and relay-passcode must be set when relay-host is configured."
                            + " Refusing to connect to STOMP broker with default credentials.");
                }
                // Production: STOMP relay to external broker (e.g. RabbitMQ).
                // Requires the STOMP broker to be reachable at ws.relayHost():ws.relayPort().
                config.enableStompBrokerRelay("/topic", "/queue")
                        .setRelayHost(ws.relayHost())
                        .setRelayPort(ws.relayPort())
                        .setClientLogin(ws.relayLogin())
                        .setClientPasscode(ws.relayPasscode());
            } else {
                // Development/single-pod: in-memory broker.
                // Events from one pod are NOT delivered to clients on other pods.
                config.enableSimpleBroker("/topic");
            }
            config.setApplicationDestinationPrefixes("/app");
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/sagaweaw-ws").withSockJS();
        }

        @Override
        public void configureClientInboundChannel(
                org.springframework.messaging.simp.config.ChannelRegistration registration) {
            String token = properties != null && properties.observability() != null
                    ? properties.observability().token()
                    : null;
            registration.interceptors(new SagaWebSocketAuthInterceptor(token));
        }
    }
}
