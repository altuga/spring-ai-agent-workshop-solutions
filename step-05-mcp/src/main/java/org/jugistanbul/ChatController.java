package org.jugistanbul;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatBot chatBot;
    private final WeatherClient weatherClient;
    private final IPLookupClient ipLookupClient;
    private final Gson gson = new Gson();
    private static final String LAT_KEY = "latitude";
    private static final String LON_KEY = "longitude";
    private static final String RESULT_KEY = "result";
    private static final String CONTENT_KEY = "content";
    private static final String CURRENT_KEY = "current";
    private static final String TIME_KEY = "time";
    private static final String TEMP_KEY = "temperature_2m";
    private static final String WIND_KEY = "wind_speed_10m";
    private static final String PRECIP_KEY = "precipitation";

    public ChatController(ChatBot chatBot, WeatherClient weatherClient, IPLookupClient ipLookupClient) {
        this.chatBot = chatBot;
        this.weatherClient = weatherClient;
        this.ipLookupClient = ipLookupClient;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage();
        String lower = message.toLowerCase().trim();

        // Location info intent: report IP-based location without weather
        if (lower.contains("where is my location") || lower.matches(".*\\bmy location\\b.*") || lower.matches(".*\\bwhere am i\\b.*")) {
            return handleLocationInfo();
        }
        // Near-me weather intents before city parsing
        if (lower.contains("near me") || lower.contains("around me") || lower.contains("weather here") || lower.contains("current weather here")) {
            return handleNearMeWeather();
        }

        String cityResult = handleCityWeather(message, lower);
        if (cityResult != null) return cityResult;

        String reply = chatBot.chat(message);
        // Sanitize any tool trace markers or pseudo-code the model may emit
        reply = reply.replaceAll("<\\|[^| ]*\\|>", ""); // remove special tag markers
        reply = reply.replace("%", ""); // strip stray percent signs
        // Remove lines that look like tool invocations or JSON blocks
        StringBuilder clean = new StringBuilder();
        for (String line : reply.split("\\r?\\n")) {
            String trimmed = line.trim();
            boolean looksLikeCall = trimmed.matches("(?i).*(getLocation|getWeatherFromLocationJson|getWeatherByCoordinates|getWeatherForCurrentLocation).*\\(.*\\).*");
            boolean looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("}") || trimmed.startsWith("[") || trimmed.startsWith("]");
            if (!looksLikeCall && !looksLikeJson) {
                clean.append(trimmed).append("\n");
            }
        }
        String result = clean.toString().trim();
        return result.isEmpty() ? "I’ve fetched the weather using tools and summarized it above." : result;
    }

    private String handleCityWeather(String message, String lower) {
        int idx = lower.indexOf("weather ");
        String city = null;
        if (idx >= 0) {
            city = message.substring(idx + 8).trim();
        } else {
            // Lean fallback patterns: "weather in <city>" or "<city> weather"
            if (lower.startsWith("weather in ")) {
                city = message.substring(message.toLowerCase().indexOf("weather in ") + 11).trim();
            } else if (lower.endsWith(" weather")) {
                city = message.substring(0, message.toLowerCase().lastIndexOf(" weather")).trim();
            }
        }
        if (city == null) return null;
        // Strip trailing punctuation
        if (city.endsWith(".") || city.endsWith("!") || city.endsWith(",")) {
            city = city.substring(0, city.length() - 1).trim();
        }
        if (city.isEmpty()) return null;
        String cityRequest = buildJsonRpcCityRequest(city);
        String response = weatherClient.callTool(cityRequest);
        String text = extractTextFromRpc(response);
        if (text != null && !text.isEmpty()) return "Weather in " + city + ": " + text;
        return sanitizeFallback(response);
    }

    private String handleNearMeWeather() {
        String locJson;
        try {
            locJson = ipLookupClient.getLocation();
        } catch (Exception _) {
            return "Location service is unavailable right now.";
        }
        JsonObject obj = gson.fromJson(locJson, JsonObject.class);
        Double lat = null;
        Double lon = null;
        if (obj.has("lat") && !obj.get("lat").isJsonNull()) lat = obj.get("lat").getAsDouble();
        if (obj.has(LAT_KEY) && !obj.get(LAT_KEY).isJsonNull()) lat = obj.get(LAT_KEY).getAsDouble();
        if (obj.has("lon") && !obj.get("lon").isJsonNull()) lon = obj.get("lon").getAsDouble();
        if (obj.has("lng") && !obj.get("lng").isJsonNull()) lon = obj.get("lng").getAsDouble();
        if (obj.has(LON_KEY) && !obj.get(LON_KEY).isJsonNull()) lon = obj.get(LON_KEY).getAsDouble();
        if (lat != null && lon != null) {
            String coordsRequest = buildJsonRpcCoordsRequest(lat, lon);
            String response = weatherClient.callTool(coordsRequest);
            String text = extractTextFromRpc(response);
            // Try to include city name from IP lookup if available
            String cityName = null;
            if (obj.has("city") && !obj.get("city").isJsonNull()) cityName = obj.get("city").getAsString();
            if (text != null && !text.isEmpty()) {
                return (cityName != null && !cityName.isBlank()) ? ("Weather in " + cityName + ": " + text) : text;
            }
            return sanitizeFallback(response);
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
        JsonObject obj = gson.fromJson(locJson, JsonObject.class);
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
        JsonObject args = new JsonObject();
        args.addProperty("city", city);
        JsonObject params = new JsonObject();
        params.addProperty("name", "Current weather by city");
        params.add("arguments", args);
        JsonObject root = new JsonObject();
        root.addProperty("jsonrpc", "2.0");
        root.addProperty("method", "tools/call");
        root.add("params", params);
        root.addProperty("id", 3);
        return gson.toJson(root);
    }

    private String buildJsonRpcCoordsRequest(double latitude, double longitude) {
        JsonObject args = new JsonObject();
        args.addProperty(LAT_KEY, String.format("%.6f", latitude));
        args.addProperty(LON_KEY, String.format("%.6f", longitude));
        JsonObject params = new JsonObject();
        params.addProperty("name", "Current weather");
        params.add("arguments", args);
        JsonObject root = new JsonObject();
        root.addProperty("jsonrpc", "2.0");
        root.addProperty("method", "tools/call");
        root.add("params", params);
        root.addProperty("id", 2);
        return gson.toJson(root);
    }

    private String extractTextFromRpc(String rpc) {
        if (rpc == null || rpc.isEmpty()) return null;
        try {
            JsonObject root = gson.fromJson(rpc, JsonObject.class);
            if (!root.has(RESULT_KEY) || !root.get(RESULT_KEY).isJsonObject()) return null;
            JsonObject result = root.getAsJsonObject(RESULT_KEY);
            if (!result.has(CONTENT_KEY) || !result.get(CONTENT_KEY).isJsonArray()) return null;
            for (var el : result.getAsJsonArray(CONTENT_KEY)) {
                if (!el.isJsonObject()) continue;
                JsonObject c = el.getAsJsonObject();
                if (c.has("text") && !c.get("text").isJsonNull()) {
                    String text = c.get("text").getAsString();
                    String summary = summarizeIfJson(text);
                    return summary != null ? summary : text;
                }
            }
        } catch (Exception _) { /* ignore: fallback below */ }
        return null;
    }

    private String summarizeIfJson(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) return null;
        try {
            JsonObject obj = gson.fromJson(trimmed, JsonObject.class);
            JsonObject current = obj.has(CURRENT_KEY) && obj.get(CURRENT_KEY).isJsonObject()
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
        // If the response contains structured data, return a simple message
        return "I’ve fetched the weather using MCP.";
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
