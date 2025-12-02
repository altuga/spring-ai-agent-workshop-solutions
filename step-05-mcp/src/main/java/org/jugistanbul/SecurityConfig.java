package org.jugistanbul;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/_static/**", "/webjars/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(org.springframework.security.config.Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
