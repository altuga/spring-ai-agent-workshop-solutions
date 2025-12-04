package com.example.mcp.weather;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "weather-client", url = "https://api.open-meteo.com")
public interface WeatherClient {

    @GetMapping("/v1/forecast")
    String forecast(@RequestParam("latitude") String latitude,
                    @RequestParam("longitude") String longitude,
                    @RequestParam("current") String current
    );
}
