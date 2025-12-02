package org.jugistanbul;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.Principal;

@Component
public class ChatBotWebSocketHandler extends TextWebSocketHandler {

    private final ChatBot chatBot;

    public ChatBotWebSocketHandler(ChatBot chatBot) {
        this.chatBot = chatBot;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Principal principal = session.getPrincipal();
        String name = (principal != null) ? principal.getName() : "User";
        String welcomeMessage = "Hi " + name + "! Welcome to your personal Spring Boot chat bot. What can I do for you?";
        session.sendMessage(new TextMessage(welcomeMessage));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String response = chatBot.chat(message.getPayload());
        if (response != null) {
            session.sendMessage(new TextMessage(response));
        }
    }
}
