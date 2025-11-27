# Step 2 - Chatbot

The next step is to build a chatbot.
This can be done by creating a new service that utilizes the Spring AI `ChatClient`.

```java
@Service
public class ChatBotService {

    private final ChatClient chatClient;

    public ChatBotService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("You are an assistant helping with users.")
                .build();
    }

    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
```

This service uses the `ChatClient` to interact with the AI model. We configure a default system message in the constructor.

Next, we need to expose this service via WebSockets. We'll create a `ChatBotWebSocketHandler` to handle the WebSocket connection and messages.

```java
public class ChatBotWebSocketHandler extends TextWebSocketHandler {

    private final ChatBotService chatBotService;

    public ChatBotWebSocketHandler(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.sendMessage(new TextMessage("Welcome to your personal Spring Boot chat bot. What can I do for you?"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String response = chatBotService.chat(message.getPayload());
        session.sendMessage(new TextMessage(response));
    }
}
```

And register the handler in a configuration class:

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatBotService chatBotService;

    public WebSocketConfig(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ChatBotWebSocketHandler(chatBotService), "/chat-bot")
                .setAllowedOrigins("*");
    }
}
```

To enable these features, ensure you have the following dependencies in your `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vertex-ai-gemini-spring-boot-starter</artifactId>
</dependency>
```

Finally, we have provided a simple `index.html` in `src/main/resources/static/index.html` to interact with the bot.

```html
<!DOCTYPE html>
<html>
<head>
    <title>Spring Boot Chatbot</title>
    <style>
        body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }
        #chat { height: 400px; border: 1px solid #ccc; overflow-y: scroll; padding: 10px; margin-bottom: 10px; }
        #message { width: 70%; padding: 10px; }
        button { padding: 10px 20px; }
        .message { margin-bottom: 10px; }
        .user { color: blue; text-align: right; }
        .bot { color: green; }
    </style>
</head>
<body>
    <h1>Spring Boot Chatbot</h1>
    <div id="chat"></div>
    <input type="text" id="message" placeholder="Type a message..." onkeypress="handleKeyPress(event)">
    <button onclick="sendMessage()">Send</button>

    <script>
        const chat = document.getElementById('chat');
        const messageInput = document.getElementById('message');
        const socket = new WebSocket('ws://localhost:8080/chat-bot');

        socket.onopen = function(event) {
            appendMessage('System', 'Connected to chat bot', 'bot');
        };

        socket.onmessage = function(event) {
            appendMessage('Bot', event.data, 'bot');
        };

        socket.onclose = function(event) {
            appendMessage('System', 'Disconnected from chat bot', 'bot');
        };

        function sendMessage() {
            const message = messageInput.value;
            if (message) {
                socket.send(message);
                appendMessage('You', message, 'user');
                messageInput.value = '';
            }
        }

        function handleKeyPress(event) {
            if (event.key === 'Enter') {
                sendMessage();
            }
        }

        function appendMessage(sender, message, type) {
            const messageElement = document.createElement('div');
            messageElement.className = `message ${type}`;
            messageElement.innerHTML = `<strong>${sender}:</strong> ${message}`;
            chat.appendChild(messageElement);
            chat.scrollTop = chat.scrollHeight;
        }
    </script>
</body>
</html>
```

Congratulations, you have built your first chatbot!
You can interact with the bot by opening your browser and navigating to http://localhost:8080/

## Model configuration

To change how the bot responds, you can modify the model parameters in the `application.properties` file.

### Temperature

The temperature of the model controls how creative the bot is.

```properties
spring.ai.ollama.chat.options.temperature=0.5
```

Try experimenting with different temperatures and see how the bot responds.
For enterprise grade chatbots avoid higher temperatures to avoid bots getting too creative.

### Configuration reference

For the rest of the workshop you can use the following configuration.

```properties
# Exclude other auto-configurations to avoid conflicts (multiple ChatModels found)
spring.autoconfigure.exclude=org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration,org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration

# Ollama Configuration
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=llama3.2

# OpenAI Configuration (Commented out)
# spring.ai.openai.api-key=${OPENAI_API_KEY}
# spring.ai.openai.chat.model=gpt-4o

# Gemini Configuration (Commented out)
# spring.ai.vertex.ai.gemini.project-id=${GEMINI_PROJECT_ID}
# spring.ai.vertex.ai.gemini.location=${GEMINI_LOCATION}
# spring.ai.vertex.ai.gemini.chat.model=gemini-1.5-flash
```

## System message

You can customize the system message in the `ChatBotService` constructor:

```java
this.chatClient = chatClientBuilder
        .defaultSystem("You are a helpful and friendly AI assistant.")
        .build();
```

Try to get creative with the system message and experiment with different personas.
