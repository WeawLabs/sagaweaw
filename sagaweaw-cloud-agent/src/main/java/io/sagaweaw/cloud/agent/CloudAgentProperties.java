package io.sagaweaw.cloud.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sagaweaw.cloud")
public class CloudAgentProperties {

    /** API key from your Sagaweaw Cloud workspace. Required to enable the agent. */
    private String apiKey;

    /** Cloud API base URL. */
    private String endpoint = "https://api.sagaweaw.dev";

    /** The environment tag sent with every event. */
    private Environment environment = Environment.PRODUCTION;

    /** How often (ms) the buffer is flushed to the Cloud. */
    private long flushIntervalMs = 5000;

    /** Maximum number of events sent per HTTP call. */
    private int batchSize = 100;

    /** Path to the local SQLite buffer file. */
    private String bufferPath = System.getProperty("java.io.tmpdir") + "/sagaweaw-events.db";

    public enum Environment { DEV, STAGING, PRODUCTION }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public long getFlushIntervalMs() { return flushIntervalMs; }
    public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public String getBufferPath() { return bufferPath; }
    public void setBufferPath(String bufferPath) { this.bufferPath = bufferPath; }
}
