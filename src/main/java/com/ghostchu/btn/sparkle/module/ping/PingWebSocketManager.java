package com.ghostchu.btn.sparkle.module.ping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.websocket.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manager for WebSocket sessions and message broadcasting.
 * <p>
 * This Spring-managed component handles:
 * - Registration and unregistration of WebSocket sessions
 * - Broadcasting messages to all connected clients
 * - Periodic logging of connection statistics
 * </p>
 * <p>
 * Thread-safety:
 * - Uses {@link CopyOnWriteArraySet} for thread-safe session management
 * - Broadcasts are handled in virtual threads for better concurrency
 * </p>
 */
@Slf4j
@Component
public class PingWebSocketManager {

    private final CopyOnWriteArraySet<Session> webSocketServerSet = new CopyOnWriteArraySet<>();
    private final AtomicLong messageId = new AtomicLong(0);
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService pingService = Executors.newScheduledThreadPool(1);

    public PingWebSocketManager(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
        pingService.scheduleWithFixedDelay(this::printConnections, 0, 5, TimeUnit.MINUTES);
    }

    private void printConnections() {
        log.info("Connected WebSocket sessions: {}", webSocketServerSet.size());
    }

    public void registerSession(@NotNull Session session) {
        webSocketServerSet.add(session);
    }

    public void unregisterSession(@NotNull Session session) {
        webSocketServerSet.remove(session);
    }

    @SneakyThrows(JsonProcessingException.class)
    public void broadcast(@NotNull Object jsonSerializable) {
        WebSocketStdMsg stdMsg = new WebSocketStdMsg(messageId.incrementAndGet(), jsonSerializable);
        String json = objectMapper.writeValueAsString(stdMsg);
        Thread.ofVirtual().name("PingWebSocket-Broadcast-" + messageId.get()).start(() -> {
            List<Future<Void>> futureList = new ArrayList<>();
            webSocketServerSet.forEach(session -> {
                if (!session.isOpen()) return;
                futureList.add(session.getAsyncRemote().sendText(json));
            });
            futureList.forEach(f -> {
                try {
                    f.get(6, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    log.warn("Interrupted when broadcasting to websocket session", e);
                } catch (ExecutionException e) {
                    log.warn("Execution exception when broadcasting to websocket session", e);
                } catch (TimeoutException e) {
                    log.warn("Timeout when broadcasting to websocket session", e);
                }
            });
        });
    }

    public record WebSocketStdMsg(long msgId, Object jsonSerializable) {
    }
}
