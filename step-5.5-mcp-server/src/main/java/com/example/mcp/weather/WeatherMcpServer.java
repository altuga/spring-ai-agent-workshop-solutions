package com.example.mcp.weather;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WeatherMcpServer {

    private static final Logger log = LoggerFactory.getLogger(WeatherMcpServer.class);
    private final WeatherClient weatherClient;
    private final GeocodingClient geocodingClient;

    public WeatherMcpServer(WeatherClient weatherClient, GeocodingClient geocodingClient) {
        this.weatherClient = weatherClient;
        this.geocodingClient = geocodingClient;
    }

    @Tool(name = "Current weather", value = "Get current weather forecast for a location.")
    public String forecast(String latitude, String longitude) {
        return weatherClient.forecast(latitude, longitude, "temperature_2m,wind_speed_10m,precipitation");
    }

    @Tool(name = "Current weather by city", value = "Get current weather forecast by city name.")
    public String forecastByCity(String city) {
        log.info("Forecast by city called: {}", city);
        GeocodingResponse response = geocodingClient.search(city, 1, "en", "json");
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return "City not found: " + city;
        }
        GeocodingResult first = response.getResults().get(0);
        String latitude = Double.toString(first.getLatitude());
        String longitude = Double.toString(first.getLongitude());
        return weatherClient.forecast(latitude, longitude, "temperature_2m,wind_speed_10m,precipitation");
    }
}
