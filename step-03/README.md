# Step 3 - Authentication

Now that we have a chatbot, we need to add authentication to it to protect it from unauthorized users.
We will leverage Spring Security with OIDC (OpenID Connect) to do that.

If you want to learn more about Spring Security and OIDC, you can check the [official documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html).

![token-flow](https://www.cncf.io/wp-content/uploads/2023/05/image-20.png)
https://www.cncf.io/blog/2023/05/17/securing-cloud-native-microservices-with-role-based-access-control-using-keycloak/

## Dependencies

First, we add the necessary Spring Boot starters for Security and OAuth2 in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-jose</artifactId>
</dependency>
```

## Keycloak Setup

Unlike Quarkus Dev Services, we will explicitly run a Keycloak container using Docker Compose.

1.  **Start Keycloak**:
    ```bash
    docker-compose up -d
    ```
    This starts Keycloak on port `9090` and imports a realm configuration from `keycloak-realm.json`.

2.  **Users**:
    The realm is pre-configured with the following users:
    -   **alice** / **alice** (User)
    -   **bob** / **bob** (Admin)

## Configuration

We configure Spring Security to use Keycloak as the Identity Provider in `src/main/resources/application.properties`:

```properties
# Security Configuration (OIDC)
spring.security.oauth2.client.registration.keycloak.client-id=backend-service
spring.security.oauth2.client.registration.keycloak.client-secret=secret
spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email
spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:9090/realms/spring-ai

# Resource Server configuration (if needed for API)
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/spring-ai
```

And we define a `SecurityConfig` class to enforce authentication in `src/main/java/org/jugistanbul/SecurityConfig.java`:

```java
package org.jugistanbul;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
            
        return http.build();
    }
}
```

## Personalised Welcome Message

We can now access the authenticated user's information in our WebSocket handler. Update `src/main/java/org/jugistanbul/ChatBotWebSocketHandler.java`:

```java
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = "User";
        if (session.getPrincipal() != null) {
            username = session.getPrincipal().getName();
        }
        
        String welcomeMessage = "Hi " + username + "! Welcome to your personal Spring Boot chat bot. What can I do for you?";
        session.sendMessage(new TextMessage(welcomeMessage));
    }
```

## Running the Application

1.  Ensure Keycloak is running (`docker-compose up -d`).
2.  Run the Spring Boot application:
    ```bash
    mvn spring-boot:run
    ```
3.  Open your browser at [http://localhost:8080](http://localhost:8080).
4.  You will be redirected to the Keycloak login page. Log in with `alice` / `alice`.
5.  You should see the personalized welcome message.

## Next Step

Now you are ready to move to the next [step](./../step-04-tools/README.md).
