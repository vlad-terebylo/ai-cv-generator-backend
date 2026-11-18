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
 * @param thinkingBudget  optional; null (the default) sends nothing about thinking, which every model accepts. 0 turns
 *                        thinking off for models that take a budget (e.g. Gemini 2.5); a model that does not accept
 *                        the field answers 400 INVALID_ARGUMENT
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
