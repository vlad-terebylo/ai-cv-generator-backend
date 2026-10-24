package org.example.aicvgenerator.client;

import java.util.List;
import java.util.Map;

import org.example.aicvgenerator.config.GeminiProperties;
import org.example.aicvgenerator.exception.AiServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GeminiClient {

    private static final int MAX_TEXT_LENGTH = 30_000;

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
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.jsonMapper = jsonMapper;
    }

    public CvExtraction extract(String documentText) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AiServiceException("Gemini API key is not configured (set GEMINI_API_KEY)");
        }
        String text = documentText.length() > MAX_TEXT_LENGTH ? documentText.substring(0, MAX_TEXT_LENGTH) : documentText;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", PROMPT + text)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA,
                        "temperature", 0));
        try {
            JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String json = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asString();
            if (!StringUtils.hasText(json)) {
                throw new AiServiceException("Gemini returned an empty response");
            }
            return jsonMapper.readValue(json, CvExtraction.class);
        } catch (RestClientException e) {
            throw new AiServiceException("Gemini request failed: " + e.getMessage(), e);
        } catch (tools.jackson.core.JacksonException e) {
            throw new AiServiceException("Gemini returned malformed JSON", e);
        }
    }
}
