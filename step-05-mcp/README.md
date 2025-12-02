# Step 5 - MCP Server

Now that we know how we can use external tools we can now move on to MCP servers.
MCP servers are a way to create a tool that any language model can use.
For enterprise usage we can imagine setting up a dedicated MCP server for an external tool.

For the above use case we would also need authentication and authorization.

For more information on MCP see the [documentation](https://modelcontextprotocol.io/docs/getting-started/intro).

## Weather tool

Now that we know our location we can use it to get the weather forecast for our location.
Instead of creating a tool directly we are going to create an MCP server.

### Spring Boot project

Start by creating a new Spring Boot project.
Navigate to https://start.spring.io/ and create a new project.
Select the following dependencies:

- `Spring Web`
- `Spring Security`
- `OpenFeign`
- `LangChain4j`

Download the project, unzip it and open it in your favorite IDE.

You should set the http port to a dedicated port to avoid conflicts with the other app:

```properties
server.port=8081
```

Now you can start the MCP server:

```shell
./mvnw spring-boot:run
```

### Weather client

Create a Feign client to get the weather forecast.

```java
@FeignClient(name = "weather-client", url = "https://api.open-meteo.com")
public interface WeatherClient {

    @GetMapping("/v1/forecast")
    String forecast(@RequestParam("latitude") String latitude,
                    @RequestParam("longitude") String longitude,
                    @RequestParam("current") String current
    );
}
```

### Weather MCP Server

Now we can create the MCP server for the weather forecast.

```java
@Component
public class WeatherMcpServer {

    private final WeatherClient weatherClient;

    public WeatherMcpServer(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    @Tool(name = "Current weather", description = "Get current weather forecast for a location.")
    public String forecast(String latitude, String longitude) {
        return weatherClient.forecast(latitude, longitude, "temperature_2m,wind_speed_10m,precipitation");
    }
}
```

Here we inject the weather client and use it to get the weather forecast.
Next we register the tool for the MCP server with the `@Tool` annotation.
Give it a good name and description so other languages can use it.

### Logging

Again we enable logging to audit what is happening:

```properties
logging.level.org.springframework.web=DEBUG
logging.level.dev.langchain4j=DEBUG
```

### Authentication

We now created a MCP server that can be used by other languages.
But we need to make sure that only authorized users can use it.

```properties
quarkus.http.auth.permission.authenticated.paths=/mcp/sse
quarkus.http.auth.permission.authenticated.policy=authenticated
```

Furthermore, you can use the `@RolesAllowed` annotation on the tool to restrict access to specific roles if needed.

After enabling authentication we now need a bearer token to access the MCP server.
You can obtain one for testing through the Quarkus Dev UI

![dev-ui-openid-connect.png](./../docs/images/dev-ui-openid-connect.png)

http://localhost:8081/q/dev-ui/quarkus-oidc/keycloak-provider

![dev-ui-keycloak.png](./../docs/images/dev-ui-keycloak-provider.png)

Sign in with either `alice:alice` or `bob:bob` and copy the access token.

Finally, you can use the access token to access the MCP server:

![mcp-inspector.png](./../docs/images/mcp-inspector.png)

### Token propagation

If the weather REST API is protected by Keycloak you can use the `quarkus-rest-client-oidc-token-propagation` extension
to propagate the token to the weather client.

```xml

<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-client-oidc-token-propagation</artifactId>
</dependency>
```

And annotate the weather client with the `@AccessToken`, see https://quarkus.io/guides/security-openid-connect-client
For our use case that is not needed right now.

### MCP Client

Now that we have the MCP server with authentication we can use it from our AI agent.
Go back to the original AI agent project and add the following extension:

```xml

<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-mcp</artifactId>
</dependency>
```

To use it you only need to add the following configuration:

```properties
quarkus.langchain4j.mcp.weather.transport-type=http
quarkus.langchain4j.mcp.weather.url=http://localhost:8081/mcp/sse/
```

And tell the AI agent to use the `weather` tool:

```java
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;

@McpToolBox("weather")
```

### MCP Client with authentication

Next we also need another extensions to automatically propagate the acces token to the MCP server:

```xml

<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-oidc-mcp-auth-provider</artifactId>
</dependency>
```

This extension provides an instance of the `McpClientAuthProvider`,
see https://docs.quarkiverse.io/quarkus-langchain4j/dev/mcp.html#_authorization

You should also disable the OIDC devservices so it we use the shared Keycloak devservices for both application:

```java
quarkus.oidc.devservices.enabled=false
```

### Run the AI agent

Finally, you can run the AI agent and use the `weather` tool.
Try asking it about the weather in your location.

> [!NOTE]
> You might want to tweak the system message for your AI agent.

## Next step

Now you are ready to move to the next [step](./../step-06-guardrails/README.md).
