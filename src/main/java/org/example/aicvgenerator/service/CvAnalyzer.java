package org.example.aicvgenerator.service;

import static org.example.aicvgenerator.service.CvAnalysisConstants.CV_CLASSIFICATION_AND_EXTRACTION_PROMPT;
import static org.example.aicvgenerator.service.CvAnalysisConstants.CV_EXTRACTION_RESPONSE_SCHEMA;
import static org.example.aicvgenerator.service.CvAnalysisConstants.MAX_DOCUMENT_TEXT_LENGTH;

import org.example.aicvgenerator.client.GeminiClient;
import org.example.aicvgenerator.exception.AiServiceException;
import org.example.aicvgenerator.service.model.CvExtraction;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class CvAnalyzer {

    private final GeminiClient geminiClient;
    private final JsonMapper jsonMapper;

    /**
     * Asks Gemini whether the text is a CV and parses it. A non-CV is not an error: it comes back with
     * {@code isCv == false}.
     *
     * @throws AiServiceException on a missing API key, a failed or timed-out call, or an empty or malformed answer
     */
    public CvExtraction analyze(String documentText) {
        String text = documentText.length() > MAX_DOCUMENT_TEXT_LENGTH
                ? documentText.substring(0, MAX_DOCUMENT_TEXT_LENGTH)
                : documentText;
        String json = geminiClient.generateJson(CV_CLASSIFICATION_AND_EXTRACTION_PROMPT + text,
                CV_EXTRACTION_RESPONSE_SCHEMA);
        try {
            return jsonMapper.readValue(json, CvExtraction.class);
        } catch (JacksonException e) {
            throw new AiServiceException("Gemini returned malformed JSON", e);
        }
    }
}
