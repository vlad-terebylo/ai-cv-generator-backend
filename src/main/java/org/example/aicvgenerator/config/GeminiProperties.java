package org.example.aicvgenerator.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the Gemini client.
 *
 * @param connectTimeout  how long to wait for the connection to Google
 * @param readTimeout     how long to wait for Google's answer; a slower call fails instead of blocking a worker thread
 * @param maxAttempts     total tries for temporary errors (503 and 429); 1 disables retrying
 * @param retryDelay      pause before the second try; it grows with every further try
 * @param thinkingBudget  tokens the model may spend "thinking" before answering; 0 turns thinking off, null leaves the
 *                        model's default (use null for a model that cannot run without thinking)
 */
@ConfigurationProperties(prefix = "cv.gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration retryDelay,
        Integer thinkingBudget) {
}
