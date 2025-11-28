package org.jugistanbul;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatBotWebSocketHandler chatBotWebSocketHandler;

    public WebSocketConfig(ChatBotWebSocketHandler chatBotWebSocketHandler) {
        this.chatBotWebSocketHandler = chatBotWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatBotWebSocketHandler, "/chat-bot")
                .setAllowedOrigins("*");
    }
}