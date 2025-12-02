package org.jugistanbul;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatBot {

    @SystemMessage("""
                You are a helpful bot that helps users with recommendations about their location.
                You can get their location and extract the latitude and longitude.
            """)
    String chat(String userMessage);
}
