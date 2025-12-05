package org.jugistanbul;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "weather-mcp", url = "http://localhost:8081")
public interface WeatherClient {

    // JSON-RPC endpoint for MCP tools
    @PostMapping(value = "/mcp/messages", consumes = "application/json")
    String callTool(@RequestBody String jsonRpcRequestBody);
}
