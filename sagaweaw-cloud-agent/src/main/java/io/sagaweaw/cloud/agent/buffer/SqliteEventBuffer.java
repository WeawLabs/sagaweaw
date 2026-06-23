package io.sagaweaw.cloud.agent.buffer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.sagaweaw.cloud.agent.model.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists events in a local SQLite database.
 * If the Cloud is unreachable, events accumulate here and are sent when connectivity returns.
 * The client application is never affected by Cloud downtime.
 */
public class SqliteEventBuffer implements EventBuffer {

    private static final Logger log = LoggerFactory.getLogger(SqliteEventBuffer.class);

    private final String jdbcUrl;
    private final ObjectMapper objectMapper;

    public SqliteEventBuffer(String bufferPath) {
        this.jdbcUrl = "jdbc:sqlite:" + bufferPath;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pending_events (
                    event_id    TEXT PRIMARY KEY,
                    type        TEXT NOT NULL,
                    saga_id     TEXT NOT NULL,
                    saga_name   TEXT NOT NULL,
                    environment TEXT NOT NULL,
                    occurred_at TEXT NOT NULL,
                    payload     TEXT NOT NULL,
                    created_at  INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
            """);
        } catch (SQLException e) {
            log.warn("[sagaweaw-cloud-agent] Failed to initialize local buffer: {}", e.getMessage());
        }
    }

    @Override
    public void store(CloudEvent event) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement("""
                INSERT OR IGNORE INTO pending_events
                    (event_id, type, saga_id, saga_name, environment, occurred_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?)
             """)) {
            stmt.setString(1, event.getEventId());
            stmt.setString(2, event.getType().name());
            stmt.setString(3, event.getSagaId());
            stmt.setString(4, event.getSagaName());
            stmt.setString(5, event.getEnvironment());
            stmt.setString(6, event.getOccurredAt().toString());
            stmt.setString(7, objectMapper.writeValueAsString(event.getPayload()));
            stmt.executeUpdate();
        } catch (SQLException | JsonProcessingException e) {
            log.warn("[sagaweaw-cloud-agent] Failed to store event locally: {}", e.getMessage());
        }
    }

    @Override
    public List<CloudEvent> drain(int limit) {
        List<CloudEvent> events = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement("""
                SELECT event_id, type, saga_id, saga_name, environment, occurred_at, payload
                FROM pending_events
                ORDER BY created_at ASC
                LIMIT ?
             """)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var payload = objectMapper.readValue(rs.getString("payload"), java.util.Map.class);
                    var event = new CloudEvent(
                            io.sagaweaw.cloud.agent.model.EventType.valueOf(rs.getString("type")),
                            rs.getString("saga_id"),
                            rs.getString("saga_name"),
                            rs.getString("environment"),
                            payload
                    );
                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.warn("[sagaweaw-cloud-agent] Failed to drain buffer: {}", e.getMessage());
        }
        return events;
    }

    @Override
    public void delete(List<String> eventIds) {
        if (eventIds.isEmpty()) return;
        String placeholders = "?,".repeat(eventIds.size()).replaceAll(",$", "");
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM pending_events WHERE event_id IN (" + placeholders + ")")) {
            for (int i = 0; i < eventIds.size(); i++) {
                stmt.setString(i + 1, eventIds.get(i));
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warn("[sagaweaw-cloud-agent] Failed to delete sent events from buffer: {}", e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
