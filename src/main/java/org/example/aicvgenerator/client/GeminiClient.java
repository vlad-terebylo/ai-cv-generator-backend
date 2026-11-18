package org.example.aicvgenerator.client;

import static org.example.aicvgenerator.config.Timing.NANOS_PER_MILLISECOND;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.aicvgenerator.config.GeminiProperties;
import org.example.aicvgenerator.exception.AiServiceException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class GeminiClient {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVICE_UNAVAILABLE = 503;

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiClient(GeminiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public String generateJson(String prompt, Map<String, Object> responseSchema) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AiServiceException("Gemini API key is not configured (set GEMINI_API_KEY)");
        }
        long start = System.nanoTime();
        JsonNode response = post(requestBody(prompt, responseSchema));
        long waitedMs = (System.nanoTime() - start) / NANOS_PER_MILLISECOND;
        String json = response == null ? ""
                : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString();
        if (!StringUtils.hasText(json)) {
            throw new AiServiceException("Gemini returned an empty response");
        }
        log.info("Gemini {} answered in {} ms", properties.model(), waitedMs);
        return json;
    }

    private Map<String, Object> requestBody(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema);
        generationConfig.put("temperature", 0);
        if (properties.thinkingBudget() != null) {
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", properties.thinkingBudget()));
        }
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);
    }

    private JsonNode post(Map<String, Object> body) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.post()
                        .uri("/models/{model}:generateContent", properties.model())
                        .header("x-goog-api-key", properties.apiKey())
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                if (!isTemporary(e) || attempt >= properties.maxAttempts()) {
                    throw new AiServiceException(String.format("Gemini request failed: %s", e.getMessage()), e);
                }
                log.warn("Gemini answered {} (attempt {} of {}), retrying", e.getStatusCode().value(), attempt,
                        properties.maxAttempts());
                pauseBeforeRetry(attempt);
            } catch (RestClientException e) {
                throw new AiServiceException(String.format("Gemini request failed: %s", e.getMessage()), e);
            }
        }
    }

    private boolean isTemporary(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return status == SERVICE_UNAVAILABLE || status == TOO_MANY_REQUESTS;
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(properties.retryDelay().multipliedBy(attempt).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("Interrupted while waiting to retry Gemini", e);
        }
    }
}
