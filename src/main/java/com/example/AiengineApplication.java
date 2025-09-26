package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example")
public class AiengineApplication {
	// minimal entrypoint so tests referencing com.example.AiengineApplication can start the context
	public static void main(String[] args) {
		SpringApplication.run(AiengineApplication.class, args);
	}
}

