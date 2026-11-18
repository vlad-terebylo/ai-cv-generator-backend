package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.aicvgenerator.client.GeminiClient;
import org.example.aicvgenerator.exception.AiServiceException;
import org.example.aicvgenerator.service.model.CvExtraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CvAnalyzerTest {

    @Mock GeminiClient geminiClient;

    final CvAnalyzer analyzer() {
        return new CvAnalyzer(geminiClient, JsonMapper.builder().build());
    }

    @Test
    void parsesTheStructuredAnswer() {
        when(geminiClient.generateJson(anyString(), anyMap())).thenReturn("""
                {"isCv": true, "firstName": "Valieria", "lastName": "Karlova", "email": "v.karlova@gmail.com",
                 "summary": "proactive", "experience": [{"startYear": 2025, "position": "Senior Java Developer",
                 "company": "Absolute"}]}""");

        CvExtraction extraction = analyzer().analyze("Valieria Karlova, Java developer");

        assertThat(extraction.isCv()).isTrue();
        assertThat(extraction.firstName()).isEqualTo("Valieria");
        assertThat(extraction.lastName()).isEqualTo("Karlova");
        assertThat(extraction.email()).isEqualTo("v.karlova@gmail.com");
        assertThat(extraction.experience()).singleElement().satisfies(job -> {
            assertThat(job.startYear()).isEqualTo(2025);
            assertThat(job.endYear()).isNull();
            assertThat(job.position()).isEqualTo("Senior Java Developer");
        });
    }

    @Test
    void understandsAnAnswerSayingTheDocumentIsNotACv() {
        when(geminiClient.generateJson(anyString(), anyMap())).thenReturn("{\"isCv\": false}");

        assertThat(analyzer().analyze("Invoice 42").isCv()).isFalse();
    }

    @Test
    void sendsThePromptFollowedByTheDocumentAndTheSchema() {
        when(geminiClient.generateJson(anyString(), anyMap())).thenReturn("{\"isCv\": false}");

        analyzer().analyze("Valieria Karlova, Java developer");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(prompt.capture(),
                eq(CvAnalysisConstants.CV_EXTRACTION_RESPONSE_SCHEMA));
        assertThat(prompt.getValue()).startsWith(CvAnalysisConstants.CV_CLASSIFICATION_AND_EXTRACTION_PROMPT)
                .endsWith("Valieria Karlova, Java developer");
    }

    @Test
    void cutsTheDocumentAtTheMaximumLength() {
        when(geminiClient.generateJson(anyString(), anyMap())).thenReturn("{\"isCv\": false}");
        String longText = "x".repeat(CvAnalysisConstants.MAX_DOCUMENT_TEXT_LENGTH + 500);

        analyzer().analyze(longText);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(prompt.capture(), anyMap());
        assertThat(prompt.getValue()).hasSize(
                CvAnalysisConstants.CV_CLASSIFICATION_AND_EXTRACTION_PROMPT.length()
                        + CvAnalysisConstants.MAX_DOCUMENT_TEXT_LENGTH);
    }

    @Test
    void failsOnAnAnswerThatIsNotValidJson() {
        when(geminiClient.generateJson(anyString(), anyMap())).thenReturn("this is not json");

        assertThatThrownBy(() -> analyzer().analyze("cv")).isInstanceOf(AiServiceException.class)
                .hasMessageContaining("malformed");
    }
}
