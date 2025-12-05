package com.example.mcp.weather;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "geocoding-client", url = "https://geocoding-api.open-meteo.com")
public interface GeocodingClient {

    @GetMapping("/v1/search")
    GeocodingResponse search(
            @RequestParam("name") String name,
            @RequestParam(value = "count", defaultValue = "1") int count,
            @RequestParam(value = "language", defaultValue = "en") String language,
            @RequestParam(value = "format", defaultValue = "json") String format
    );
}
