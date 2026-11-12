package org.example.aicvgenerator.client;

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
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class GeminiClient {

    private static final int MAX_TEXT_LENGTH = 30_000;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVICE_UNAVAILABLE = 503;

    private static final String PROMPT = """
            You are a CV (resume) parser. Decide whether the document below is a CV/resume of a person.
            If it is not, set isCv to false and leave every other field empty.
            If it is, set isCv to true and extract: firstName, lastName, email, a one or two sentence summary,
            and the work experience list. For each position give startYear, endYear (null if it is the current
            position), position title and company. Do not invent data that is not in the document.

            DOCUMENT:
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "isCv", Map.of("type", "BOOLEAN"),
                    "firstName", Map.of("type", "STRING", "nullable", true),
                    "lastName", Map.of("type", "STRING", "nullable", true),
                    "email", Map.of("type", "STRING", "nullable", true),
                    "summary", Map.of("type", "STRING", "nullable", true),
                    "experience", Map.of("type", "ARRAY", "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                    "startYear", Map.of("type", "INTEGER", "nullable", true),
                                    "endYear", Map.of("type", "INTEGER", "nullable", true),
                                    "position", Map.of("type", "STRING"),
                                    "company", Map.of("type", "STRING", "nullable", true)),
                            "required", List.of("position")))),
            "required", List.of("isCv"));

    private final GeminiProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public GeminiClient(GeminiProperties properties, RestClient.Builder restClientBuilder, JsonMapper jsonMapper) {
        this.properties = properties;
        // Explicit timeouts: without them a stalled Google call would hold a worker thread (and its job in
        // PROCESSING) for as long as the connection stays open.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
        this.jsonMapper = jsonMapper;
    }

    public CvExtraction extract(String documentText) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AiServiceException("Gemini API key is not configured (set GEMINI_API_KEY)");
        }
        String text = documentText.length() > MAX_TEXT_LENGTH ? documentText.substring(0, MAX_TEXT_LENGTH) : documentText;
        long start = System.nanoTime();
        JsonNode response = post(requestBody(text));
        long waitedMs = (System.nanoTime() - start) / 1_000_000;
        String json = response == null ? ""
                : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString();
        if (!StringUtils.hasText(json)) {
            throw new AiServiceException("Gemini returned an empty response");
        }
        try {
            CvExtraction extraction = jsonMapper.readValue(json, CvExtraction.class);
            log.info("Gemini {} answered in {} ms", properties.model(), waitedMs);
            return extraction;
        } catch (tools.jackson.core.JacksonException e) {
            throw new AiServiceException("Gemini returned malformed JSON", e);
        }
    }

    private Map<String, Object> requestBody(String text) {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", RESPONSE_SCHEMA);
        generationConfig.put("temperature", 0);
        if (properties.thinkingBudget() != null) {
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", properties.thinkingBudget()));
        }
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", PROMPT + text)))),
                "generationConfig", generationConfig);
    }

    /** Sends the request; only Google's temporary errors (503, 429) are retried, everything else fails at once. */
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
                    throw new AiServiceException("Gemini request failed: " + e.getMessage(), e);
                }
                log.warn("Gemini answered {} (attempt {} of {}), retrying", e.getStatusCode().value(), attempt,
                        properties.maxAttempts());
                pauseBeforeRetry(attempt);
            } catch (RestClientException e) {
                // timeouts and connection problems: retrying would only multiply the wait
                throw new AiServiceException("Gemini request failed: " + e.getMessage(), e);
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
