package com.ghostchu.btn.sparkle.module.ping;

import com.ghostchu.btn.sparkle.config.SpringWebSocketServerEndpointConfigurator;
import com.ghostchu.btn.sparkle.module.userapp.UserApplicationService;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * WebSocket endpoint for handling ping event streams.
 * <p>
 * This class uses {@link SpringWebSocketServerEndpointConfigurator} to enable Spring dependency injection
 * in Jakarta WebSocket endpoints. The configurator ensures that Spring manages the lifecycle of this
 * endpoint and properly injects all required dependencies.
 * </p>
 * <p>
 * Architecture:
 * - {@link SpringWebSocketServerEndpointConfigurator} bridges Jakarta WebSocket and Spring container
 * - Dependencies are injected via constructor (constructor injection)
 * - {@link PingWebSocketManager} manages all active WebSocket sessions and handles broadcasting
 * - Uses prototype scope to create a new instance for each WebSocket connection
 * </p>
 */
@Slf4j
@Component
@Scope("prototype")
@ServerEndpoint(value = "/ping/eventStream", configurator = SpringWebSocketServerEndpointConfigurator.class)
public class PingWebSocketSession {
    private final UserApplicationService userApplicationService;
    private final PingWebSocketManager pingWebSocketManager;

    private Session session;

    /**
     * Constructor for Spring dependency injection.
     *
     * @param userApplicationService Service for user application authentication
     * @param pingWebSocketManager Manager for WebSocket session lifecycle and broadcasting
     */
    public PingWebSocketSession(UserApplicationService userApplicationService,
                                 PingWebSocketManager pingWebSocketManager) {
        this.userApplicationService = userApplicationService;
        this.pingWebSocketManager = pingWebSocketManager;
    }


    @OnOpen
    public void onOpen(Session session) throws IOException {
        this.session = session;
        var queryParameters = session.getRequestParameterMap();
        String appId = queryParameters.get("appId").getFirst();
        String appSecret = queryParameters.get("appSecret").getFirst();
        var userAppOptional = userApplicationService.getUserApplication(appId, appSecret);
        if (userAppOptional.isEmpty()) {
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "UserApplication authorize failed"));
            return;
        }
        var userApp = userAppOptional.get();
        if (userApp.getBannedAt() != null || userApp.getUser().getBannedAt() != null) {
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "UserApplication is banned by administrator"));
            return;
        }
        session.getContainer().setAsyncSendTimeout(5 * 1000);
        pingWebSocketManager.registerSession(session);
        log.info("Connected to WebSocket: userAppId={}, userId={}; Session: {}", userApp.getId(), userApp.getUser().getId(), session);
    }

    @OnClose
    public void onClose() {
        pingWebSocketManager.unregisterSession(session);
        log.info("Disconnected from WebSocket: Session: {}", session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("Unhandled message from WebSocket: Session: {}, Message: {}", session, message);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        pingWebSocketManager.unregisterSession(session);
        log.warn("Error occurred in WebSocket: Session: {}", session, error);
    }

}
