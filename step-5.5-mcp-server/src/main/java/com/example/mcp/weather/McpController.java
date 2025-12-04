package com.example.mcp.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private final WeatherMcpServer weatherMcpServer;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolInfo> tools = new HashMap<>();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    static class ToolInfo {
        public ToolSpecification spec;
        public Method method;

        public ToolInfo(ToolSpecification spec, Method method) {
            this.spec = spec;
            this.method = method;
        }
    }

    public McpController(WeatherMcpServer weatherMcpServer, ObjectMapper objectMapper) {
        this.weatherMcpServer = weatherMcpServer;
        this.objectMapper = objectMapper;
        initTools();
    }

    private void initTools() {
        try {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(weatherMcpServer);
            for (Method method : weatherMcpServer.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    String toolName = toolAnnotation.name().isEmpty() ? method.getName() : toolAnnotation.name();
                    
                    for (ToolSpecification spec : specs) {
                        if (spec.name().equals(toolName)) {
                            tools.put(toolName, new ToolInfo(spec, method));
                            log.info("Registered tool: {}", toolName);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error initializing tools", e);
        }
    }

    @GetMapping("/sse")
    public SseEmitter handleSse(HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        sseExecutor.execute(() -> {
            try {
                String endpointUrl = "/mcp/messages"; 
                emitter.send(SseEmitter.event().name("endpoint").data(endpointUrl));
                log.info("Sent endpoint event: {}", endpointUrl);
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/messages")
    public JsonRpcResponse handleMessage(@RequestBody JsonRpcRequest request) {
        log.info("Received message: {}", request);
        
        try {
            if ("initialize".equals(request.method())) {
                return new JsonRpcResponse("2.0", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "Weather MCP Server", "version", "1.0.0")
                ), null, request.id());
            } else if ("notifications/initialized".equals(request.method())) {
                return null; 
            } else if ("tools/list".equals(request.method())) {
                List<Map<String, Object>> toolsList = new ArrayList<>();
                for (ToolInfo info : tools.values()) {
                    toolsList.add(toolSpecToMap(info.spec));
                }
                return new JsonRpcResponse("2.0", Map.of("tools", toolsList), null, request.id());
            } else if ("tools/call".equals(request.method())) {
                JsonNode paramsNode = objectMapper.valueToTree(request.params());
                String name = paramsNode.get("name").asText();
                JsonNode arguments = paramsNode.get("arguments");
                
                ToolInfo toolInfo = tools.get(name);
                if (toolInfo == null) {
                    throw new IllegalArgumentException("Tool not found: " + name);
                }
                
                // Execute the tool
                Object result = executeTool(toolInfo.method, arguments);
                
                return new JsonRpcResponse("2.0", Map.of(
                        "content", List.of(Map.of("type", "text", "text", result.toString()))
                ), null, request.id());
            } else if ("ping".equals(request.method())) {
                return new JsonRpcResponse("2.0", Map.of(), null, request.id());
            }
            
            return new JsonRpcResponse("2.0", null, Map.of("code", -32601, "message", "Method not found"), request.id());
            
        } catch (Exception e) {
            log.error("Error handling message", e);
            return new JsonRpcResponse("2.0", null, Map.of("code", -32000, "message", e.getMessage()), request.id());
        }
    }

    private Object executeTool(Method method, JsonNode argumentsNode) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            if (argumentsNode.has(paramName)) {
                String value = argumentsNode.get(paramName).asText();
                args[i] = value;
            }
        }
        
        method.setAccessible(true);
        return method.invoke(weatherMcpServer, args);
    }

    private Map<String, Object> toolSpecToMap(ToolSpecification spec) {
        Map<String, Object> toolMap = new LinkedHashMap<>();
        toolMap.put("name", spec.name());
        toolMap.put("description", spec.description() != null ? spec.description() : "");
        
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        
        // For simplicity, just create basic properties without accessing ToolParameter objects
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("latitude", Map.of("type", "string", "description", "Latitude"));
        properties.put("longitude", Map.of("type", "string", "description", "Longitude"));
        
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("latitude", "longitude"));
        
        toolMap.put("inputSchema", inputSchema);
        return toolMap;
    }

    public record JsonRpcRequest(String jsonrpc, String method, Object params, Object id) {}
    public record JsonRpcResponse(String jsonrpc, Object result, Object error, Object id) {}
}
