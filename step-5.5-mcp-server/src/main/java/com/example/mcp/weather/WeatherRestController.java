package com.example.mcp.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

public class WeatherRestController {

    private static final Logger log = LoggerFactory.getLogger(WeatherRestController.class);

    private final WeatherMcpServer weatherMcpServer;
    private final GeocodingClient geocodingClient;

    public WeatherRestController(WeatherMcpServer weatherMcpServer, GeocodingClient geocodingClient) {
        this.weatherMcpServer = weatherMcpServer;
        this.geocodingClient = geocodingClient;
    }

        // This class is intentionally left without Spring annotations,
        // so it is not exposed as a REST controller.
        public ResponseEntity<String> getWeather(
            String lat,
            String lon,
            String latitude,
            String longitude,
            String city
        ) {
        
        log.info("Received params lat={}, lon={}, latitude={}, longitude={}, city={}", lat, lon, latitude, longitude, city);
        // Prefer lat/lon, fallback to latitude/longitude params
        String resolvedLat = lat != null ? lat : latitude;
        String resolvedLon = lon != null ? lon : longitude;

        // If city provided but no coordinates, try geocoding
        if ((resolvedLat == null || resolvedLon == null) && city != null) {
            GeocodingResponse response = geocodingClient.search(city, 1, "en", "json");
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return ResponseEntity.status(404).body("City not found: " + city);
            }
            GeocodingResult first = response.getResults().get(0);
            resolvedLat = Double.toString(first.getLatitude());
            resolvedLon = Double.toString(first.getLongitude());
        }

        if (resolvedLat == null || resolvedLon == null) {
            return ResponseEntity.badRequest().body("Missing required query params: lat and lon (or latitude and longitude)");
        }

        String result = weatherMcpServer.forecast(resolvedLat, resolvedLon);
        return ResponseEntity.ok(result);
    }
}
