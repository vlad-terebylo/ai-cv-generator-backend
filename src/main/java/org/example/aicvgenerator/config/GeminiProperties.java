package org.example.aicvgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cv.gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl) {
}
