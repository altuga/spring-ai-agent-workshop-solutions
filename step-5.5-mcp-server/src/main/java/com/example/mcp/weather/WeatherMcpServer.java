package com.example.mcp.weather;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class WeatherMcpServer {

    private final WeatherClient weatherClient;

    public WeatherMcpServer(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    @Tool(name = "Current weather", value = "Get current weather forecast for a location.")
    public String forecast(String latitude, String longitude) {
        return weatherClient.forecast(latitude, longitude, "temperature_2m,wind_speed_10m,precipitation");
    }
}
