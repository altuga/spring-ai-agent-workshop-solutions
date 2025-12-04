package org.jugistanbul;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatBot chatBot;

    public ChatController(ChatBot chatBot) {
        this.chatBot = chatBot;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return chatBot.chat(request.getMessage());
    }

    public static class ChatRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
