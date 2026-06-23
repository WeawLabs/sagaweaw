package io.sagaweaw.cloud.agent;

import io.sagaweaw.cloud.agent.buffer.EventBuffer;
import io.sagaweaw.cloud.agent.buffer.SqliteEventBuffer;
import io.sagaweaw.cloud.agent.listener.CloudEventListener;
import io.sagaweaw.cloud.agent.scheduler.FlushScheduler;
import io.sagaweaw.cloud.agent.sender.CloudEventSender;
import io.sagaweaw.cloud.agent.sender.HttpCloudEventSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configures the Sagaweaw Cloud Agent when sagaweaw.cloud.api-key is present.
 * If the property is missing, the entire agent is disabled — zero impact on the application.
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(CloudAgentProperties.class)
@ConditionalOnProperty(prefix = "sagaweaw.cloud", name = "api-key")
public class CloudAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CloudAgentAutoConfiguration.class);

    @Bean
    public EventBuffer cloudEventBuffer(CloudAgentProperties properties) {
        log.info("[sagaweaw-cloud-agent] Local buffer initialized at: {}", properties.getBufferPath());
        return new SqliteEventBuffer(properties.getBufferPath());
    }

    @Bean
    public CloudEventSender cloudEventSender(CloudAgentProperties properties) {
        log.info("[sagaweaw-cloud-agent] Connected to Cloud at: {} (env: {})",
                properties.getEndpoint(), properties.getEnvironment());
        return new HttpCloudEventSender(properties.getEndpoint(), properties.getApiKey());
    }

    @Bean
    public CloudEventListener cloudEventListener(EventBuffer buffer, CloudAgentProperties properties) {
        return new CloudEventListener(buffer, properties);
    }

    @Bean
    public FlushScheduler cloudFlushScheduler(EventBuffer buffer, CloudEventSender sender,
                                               CloudAgentProperties properties) {
        return new FlushScheduler(buffer, sender, properties);
    }
}
