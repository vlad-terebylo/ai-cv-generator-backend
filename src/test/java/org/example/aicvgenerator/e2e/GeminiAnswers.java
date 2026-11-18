package org.example.aicvgenerator.e2e;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/** The JSON text Gemini would answer with, as the mocked {@code GeminiClient} returns it. */
final class GeminiAnswers {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GeminiAnswers() {
    }

    static String cv(String email, String summary, String position) {
        return JSON.writeValueAsString(Map.of(
                "isCv", true, "firstName", "Valieria", "lastName", "Karlova", "email", email, "summary", summary,
                "experience", List.of(Map.of("startYear", 2025, "position", position, "company", "Absolute"))));
    }

    static String notACv() {
        return "{\"isCv\": false}";
    }
}
