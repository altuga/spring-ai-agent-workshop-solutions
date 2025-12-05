package org.jugistanbul;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatBot {

        @SystemMessage("""
                        You are a helpful, concise assistant.
                        - Present clear, natural-language answers only.
                        - Do not include code blocks, pseudo-code, or tool traces.
                        - When asked for weather or location, respond briefly and avoid fabricating details.
                        - If unsure, ask a short clarifying question.
                        """)
    String chat(String userMessage);
}
