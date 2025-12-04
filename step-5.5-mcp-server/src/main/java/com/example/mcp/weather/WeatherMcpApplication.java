package com.example.mcp.weather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class WeatherMcpApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeatherMcpApplication.class, args);
	}

}
