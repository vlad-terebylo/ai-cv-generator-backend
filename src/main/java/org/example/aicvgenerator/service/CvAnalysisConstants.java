package org.example.aicvgenerator.service;

import java.util.List;
import java.util.Map;

/** The CV-specific parts of the Gemini request: what to ask and the shape of the answer. */
final class CvAnalysisConstants {

    /** Longer documents are cut silently: the tail of a very long CV is never sent to Gemini. */
    static final int MAX_DOCUMENT_TEXT_LENGTH = 30_000;

    /** Ends with "DOCUMENT:"; the document text is appended directly after it. */
    static final String CV_CLASSIFICATION_AND_EXTRACTION_PROMPT = """
            You are a CV (resume) parser. Decide whether the document below is a CV/resume of a person.
            If it is not, set isCv to false and leave every other field empty.
            If it is, set isCv to true and extract: firstName, lastName, email, a one or two sentence summary,
            and the work experience list. For each position give startYear, endYear (null if it is the current
            position), position title and company. Do not invent data that is not in the document.

            DOCUMENT:
            """;

    /** Mirrors {@code CvExtraction}: a field renamed there must be renamed here too. */
    static final Map<String, Object> CV_EXTRACTION_RESPONSE_SCHEMA = Map.of(
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

    private CvAnalysisConstants() {
    }
}
