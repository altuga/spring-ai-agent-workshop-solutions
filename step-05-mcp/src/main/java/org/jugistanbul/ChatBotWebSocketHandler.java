package org.jugistanbul;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.security.Principal;

@Component
public class ChatBotWebSocketHandler extends TextWebSocketHandler {

    private final ChatBot chatBot;
    private final WeatherClient weatherClient;
    private final IPLookupClient ipLookupClient;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private static final String LAT_KEY = "latitude";
    private static final String LON_KEY = "longitude";
    private static final String RESULT_KEY = "result";
    private static final String CONTENT_KEY = "content";
    private static final String CURRENT_KEY = "current";
    private static final String TIME_KEY = "time";
    private static final String TEMP_KEY = "temperature_2m";
    private static final String WIND_KEY = "wind_speed_10m";
    private static final String PRECIP_KEY = "precipitation";

    public ChatBotWebSocketHandler(ChatBot chatBot, WeatherClient weatherClient, IPLookupClient ipLookupClient) {
        this.chatBot = chatBot;
        this.weatherClient = weatherClient;
        this.ipLookupClient = ipLookupClient;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Principal principal = session.getPrincipal();
        String name = (principal != null) ? principal.getName() : "User";
        String welcomeMessage = "Hi " + name + "! Welcome to your personal Spring Boot chat bot. What can I do for you?";
        session.sendMessage(new TextMessage(welcomeMessage));
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload() == null ? "" : message.getPayload();
        // Strip simple HTML tags that the UI may wrap around text (e.g., <p>Weather Ankara</p>)
        payload = payload.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        String lower = payload.toLowerCase().trim();

        // Location info intent: report IP-based location (async)
        if (lower.contains("where is my location") || lower.matches(".*\\bmy location\\b.*") || lower.matches(".*\\bwhere am i\\b.*")) {
            session.sendMessage(new TextMessage("Fetching your IP location…"));
            java.util.concurrent.CompletableFuture
                .supplyAsync(this::handleLocationInfo)
                .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(_ -> "Location service is unavailable right now.")
                .thenAccept(result -> {
                    try { session.sendMessage(new TextMessage(result != null ? result : "")); } catch (Exception ignored) { }
                });
            return;
        }
        // Near-me weather intent (async)
        if (lower.contains("near me") || lower.contains("around me") || lower.contains("weather here") || lower.contains("current weather here")) {
            session.sendMessage(new TextMessage("Fetching weather for your location…"));
            java.util.concurrent.CompletableFuture
                .supplyAsync(this::handleNearMeWeather)
                .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(_ -> "Location service is unavailable right now.")
                .thenAccept(result -> {
                    try { session.sendMessage(new TextMessage(result != null ? result : "")); } catch (Exception ignored) { }
                });
            return;
        }

        // City-based: "weather <city>", "weather in <city>", or "<city> weather"
        String city = null;
        int idx = lower.indexOf("weather ");
        if (idx >= 0) {
            city = payload.substring(idx + 8).trim();
        } else {
            if (lower.startsWith("weather in ")) {
                city = payload.substring(payload.toLowerCase().indexOf("weather in ") + 11).trim();
            } else if (lower.endsWith(" weather")) {
                city = payload.substring(0, payload.toLowerCase().lastIndexOf(" weather")).trim();
            }
        }
        if (city != null) {
            if (city.endsWith(".") || city.endsWith("!") || city.endsWith(",")) {
                city = city.substring(0, city.length() - 1).trim();
            }
            if (!city.isEmpty()) {
                String cityReq = buildJsonRpcCityRequest(city);
                String rpc = weatherClient.callTool(cityReq);
                String text = extractTextFromRpc(rpc);
                String out = (text != null && !text.isEmpty()) ? ("Weather in " + city + ": " + text) : sanitizeFallback(rpc);
                session.sendMessage(new TextMessage(out != null ? out : ""));
                return;
            }
        }

        String response = chatBot.chat(payload);
        if (response != null) {
            // Sanitize model output similarly to HTTP controller
            response = response.replaceAll("<\\|[^|]*\\|>", "");
            response = response.replace("%", "");
            StringBuilder clean = new StringBuilder();
            for (String line : response.split("\\r?\\n")) {
                String trimmed = line.trim();
                boolean looksLikeCall = trimmed.matches("(?i).*(getLocation|getWeatherFromLocationJson|getWeatherByCoordinates|getWeatherForCurrentLocation|getWeatherByCity).*\\(.*\\).*");
                boolean looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("}") || trimmed.startsWith("[") || trimmed.startsWith("]");
                if (!looksLikeCall && !looksLikeJson) {
                    clean.append(trimmed).append("\n");
                }
            }
            String result = clean.toString().trim();
            session.sendMessage(new TextMessage(result.isEmpty() ? "Request processed." : result));
        }
    }

    private String handleNearMeWeather() {
        String locJson;
        try {
            locJson = ipLookupClient.getLocation();
        } catch (Exception _) {
            return "Location service is unavailable right now.";
        }
        com.google.gson.JsonObject obj = gson.fromJson(locJson, com.google.gson.JsonObject.class);
        Double lat = null;
        Double lon = null;
        if (obj.has("lat") && !obj.get("lat").isJsonNull()) lat = obj.get("lat").getAsDouble();
        if (obj.has(LAT_KEY) && !obj.get(LAT_KEY).isJsonNull()) lat = obj.get(LAT_KEY).getAsDouble();
        if (obj.has("lon") && !obj.get("lon").isJsonNull()) lon = obj.get("lon").getAsDouble();
        if (obj.has("lng") && !obj.get("lng").isJsonNull()) lon = obj.get("lng").getAsDouble();
        if (obj.has(LON_KEY) && !obj.get(LON_KEY).isJsonNull()) lon = obj.get(LON_KEY).getAsDouble();
        if (lat != null && lon != null) {
            String req = buildJsonRpcCoordsRequest(lat, lon);
            String rpc = weatherClient.callTool(req);
            String text = extractTextFromRpc(rpc);
            String cityName = null;
            if (obj.has("city") && !obj.get("city").isJsonNull()) cityName = obj.get("city").getAsString();
            if (text != null && !text.isEmpty()) {
                return (cityName != null && !cityName.isBlank()) ? ("Weather in " + cityName + ": " + text) : text;
            }
            return sanitizeFallback(rpc);
        }
        return "Could not determine coordinates from IP location.";
    }

    private String handleLocationInfo() {
        String locJson;
        try {
            locJson = ipLookupClient.getLocation();
        } catch (Exception _) {
            return "Location service is unavailable right now.";
        }
        com.google.gson.JsonObject obj = gson.fromJson(locJson, com.google.gson.JsonObject.class);
        String city = obj.has("city") && !obj.get("city").isJsonNull() ? obj.get("city").getAsString() : null;
        Double lat = null;
        Double lon = null;
        if (obj.has("lat") && !obj.get("lat").isJsonNull()) lat = obj.get("lat").getAsDouble();
        if (obj.has(LAT_KEY) && !obj.get(LAT_KEY).isJsonNull()) lat = obj.get(LAT_KEY).getAsDouble();
        if (obj.has("lon") && !obj.get("lon").isJsonNull()) lon = obj.get("lon").getAsDouble();
        if (obj.has("lng") && !obj.get("lng").isJsonNull()) lon = obj.get("lng").getAsDouble();
        if (obj.has(LON_KEY) && !obj.get(LON_KEY).isJsonNull()) lon = obj.get(LON_KEY).getAsDouble();
        if (lat == null || lon == null) return "Could not determine coordinates from IP location.";
        StringBuilder sb = new StringBuilder();
        sb.append("You are in ");
        if (city != null && !city.isBlank()) sb.append(city).append(" ");
        sb.append(String.format("(lat %.6f, lon %.6f)", lat, lon));
        return sb.toString();
    }

    private String buildJsonRpcCityRequest(String city) {
        com.google.gson.JsonObject args = new com.google.gson.JsonObject();
        args.addProperty("city", city);
        com.google.gson.JsonObject params = new com.google.gson.JsonObject();
        params.addProperty("name", "Current weather by city");
        params.add("arguments", args);
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("jsonrpc", "2.0");
        root.addProperty("method", "tools/call");
        root.add("params", params);
        root.addProperty("id", 5);
        return gson.toJson(root);
    }

    private String buildJsonRpcCoordsRequest(double latitude, double longitude) {
        com.google.gson.JsonObject args = new com.google.gson.JsonObject();
        args.addProperty(LAT_KEY, String.format("%.6f", latitude));
        args.addProperty(LON_KEY, String.format("%.6f", longitude));
        com.google.gson.JsonObject params = new com.google.gson.JsonObject();
        params.addProperty("name", "Current weather");
        params.add("arguments", args);
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("jsonrpc", "2.0");
        root.addProperty("method", "tools/call");
        root.add("params", params);
        root.addProperty("id", 6);
        return gson.toJson(root);
    }

    private String extractTextFromRpc(String rpc) {
        if (rpc == null || rpc.isEmpty()) return null;
        try {
            com.google.gson.JsonObject root = gson.fromJson(rpc, com.google.gson.JsonObject.class);
            if (!root.has(RESULT_KEY) || !root.get(RESULT_KEY).isJsonObject()) return null;
            com.google.gson.JsonObject result = root.getAsJsonObject(RESULT_KEY);
            if (!result.has(CONTENT_KEY) || !result.get(CONTENT_KEY).isJsonArray()) return null;
            for (var el : result.getAsJsonArray(CONTENT_KEY)) {
                if (!el.isJsonObject()) continue;
                com.google.gson.JsonObject c = el.getAsJsonObject();
                if (c.has("text") && !c.get("text").isJsonNull()) {
                    String text = c.get("text").getAsString();
                    String summary = summarizeIfJson(text);
                    return summary != null ? summary : text;
                }
            }
        } catch (Exception _) { /* ignore */ }
        return null;
    }

    private String summarizeIfJson(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) return null;
        try {
            com.google.gson.JsonObject obj = gson.fromJson(trimmed, com.google.gson.JsonObject.class);
            com.google.gson.JsonObject current = obj.has(CURRENT_KEY) && obj.get(CURRENT_KEY).isJsonObject()
                    ? obj.getAsJsonObject(CURRENT_KEY) : null;
            if (current == null) return null;
            String time = current.has(TIME_KEY) && !current.get(TIME_KEY).isJsonNull()
                    ? current.get(TIME_KEY).getAsString() : "unknown time";
            Double temp = current.has(TEMP_KEY) && !current.get(TEMP_KEY).isJsonNull()
                    ? current.get(TEMP_KEY).getAsDouble() : null;
            Double wind = current.has(WIND_KEY) && !current.get(WIND_KEY).isJsonNull()
                    ? current.get(WIND_KEY).getAsDouble() : null;
            Double precip = current.has(PRECIP_KEY) && !current.get(PRECIP_KEY).isJsonNull()
                    ? current.get(PRECIP_KEY).getAsDouble() : null;
            StringBuilder sb = new StringBuilder("Current weather: ");
            boolean hasPrev = false;
            if (temp != null) {
                sb.append(String.format("%.1f°C", temp));
                hasPrev = true;
            }
            if (wind != null) {
                if (hasPrev) sb.append(", ");
                sb.append(String.format("wind %.1f km/h", wind));
                hasPrev = true;
            }
            if (precip != null) {
                if (hasPrev) sb.append(", ");
                sb.append(String.format("precipitation %.2f mm", precip));
            }
            sb.append(" (as of ").append(time).append(")");
            return sb.toString();
        } catch (Exception _) {
            return null;
        }
    }

    private String sanitizeFallback(String response) {
        if (response == null) return "";
        return "I’ve fetched the weather using MCP.";
    }
}
