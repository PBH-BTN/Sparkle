package com.ghostchu.btn.sparkle.module.ping;

import com.ghostchu.btn.sparkle.module.userapp.UserApplicationService;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@ServerEndpoint("/ping/eventStream")
public class PingWebSocketSession {

    private final UserApplicationService userApplicationService;
    private final PingWebSocketManager pingWebSocketManager;
    private Session session;

    public PingWebSocketSession(UserApplicationService userApplicationService, PingWebSocketManager pingWebSocketManager) {
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
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE,"UserApplication authorize failed" ));
            return;
        }
        var userApp = userAppOptional.get();
        if (userApp.getBannedAt() != null || userApp.getUser().getBannedAt() != null) {
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE,"UserApplication is banned by administrator" ));
            return;
        }
        session.getContainer().setAsyncSendTimeout(5*1000);
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
