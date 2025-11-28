package org.jugistanbul;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class ChatBotWebSocketHandler extends TextWebSocketHandler {

    private final ChatBot chatBot;

    public ChatBotWebSocketHandler(ChatBot chatBot) {
        this.chatBot = chatBot;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = "User";
        if (session.getPrincipal() != null) {
            username = session.getPrincipal().getName();
        }
        
        String welcomeMessage = "Hi " + username + "! Welcome to your personal Spring Boot chat bot. What can I do for you?";
        session.sendMessage(new TextMessage(welcomeMessage));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String response = chatBot.chat(message.getPayload());
        session.sendMessage(new TextMessage(response));
    }
}