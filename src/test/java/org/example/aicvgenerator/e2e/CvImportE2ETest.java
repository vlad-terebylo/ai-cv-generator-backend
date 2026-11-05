package org.example.aicvgenerator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.example.aicvgenerator.client.CvExtraction;
import org.example.aicvgenerator.client.GeminiClient;
import org.example.aicvgenerator.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;

/** The whole flow over real HTTP with real files and the real executor; only Gemini is mocked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CvImportE2ETest {

    @LocalServerPort
    int port;

    @MockitoBean
    GeminiClient gemini;

    CvImportApi api;

    @BeforeEach
    void setUp() {
        api = new CvImportApi(port);
    }

    private static CvExtraction cv(String email, String summary, String position) {
        return new CvExtraction(true, "Valieria", "Karlova", email, summary,
                List.of(new CvExtraction.ExtractedExperience(2025, null, position, "Absolute")));
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    @Test
    void postReturnsImmediatelyWhileGeminiIsStillWorking() throws Exception {
        String email = uniqueEmail();
        CountDownLatch geminiMayFinish = new CountDownLatch(1);
        when(gemini.extract(anyString())).thenAnswer(invocation -> {
            geminiMayFinish.await();
            return cv(email, "proactive", "Senior Java Developer");
        });

        try {
            ResponseEntity<JsonNode> accepted = api.upload("cv.pdf", CvFixtures.pdf("Valieria Karlova, Java developer"));

            // Gemini is still blocked, yet the request already returned
            assertThat(geminiMayFinish.getCount()).isEqualTo(1);
            assertThat(accepted.getStatusCode().value()).isEqualTo(202);
            String jobId = accepted.getBody().get("id").asString();
            assertThat(accepted.getHeaders().getLocation()).hasToString("/api/v1/cv-imports/" + jobId);
            assertThat(accepted.getBody().get("status").asString()).isEqualTo("PENDING");
            assertThat(api.importStatus(jobId).getBody().get("status").asString()).isIn("PENDING", "PROCESSING");

            geminiMayFinish.countDown();
            JsonNode finished = api.awaitFinished(jobId);

            assertThat(finished.get("status").asString()).isEqualTo("COMPLETED");
            ResponseEntity<JsonNode> stored = api.cv(finished.get("cvId").asLong());
            assertThat(stored.getStatusCode().value()).isEqualTo(200);
            assertThat(stored.getBody().get("email").asString()).isEqualTo(email);
            assertThat(stored.getBody().get("firstName").asString()).isEqualTo("Valieria");
            assertThat(stored.getBody().get("experience").get(0).get("position").asString())
                    .isEqualTo("Senior Java Developer");
        } finally {
            geminiMayFinish.countDown();
        }
    }

    @Test
    void importsADocxToo() {
        String email = uniqueEmail();
        when(gemini.extract(anyString())).thenReturn(cv(email, "docx summary", "Java Developer"));

        ResponseEntity<JsonNode> accepted = api.upload("cv.docx", CvFixtures.docx("Valieria Karlova, Java developer"));
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(api.cv(finished.get("cvId").asLong()).getBody().get("summary").asString()).isEqualTo("docx summary");
    }

    @Test
    void jobFailsWhenTheDocumentIsNotACv() {
        when(gemini.extract(anyString())).thenReturn(new CvExtraction(false, null, null, null, null, null));

        ResponseEntity<JsonNode> accepted = api.upload("invoice.pdf", CvFixtures.pdf("Invoice 42, total 100 EUR"));
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("FAILED");
        assertThat(finished.get("errorCode").asString()).isEqualTo("NOT_A_CV");
        assertThat(finished.get("cvId").isNull()).isTrue();
    }

    @Test
    void jobFailsWhenTheFileHasNoReadableTextAndGeminiIsNeverCalled() {
        ResponseEntity<JsonNode> accepted = api.upload("broken.pdf", new byte[] {1, 2, 3});
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("FAILED");
        assertThat(finished.get("errorCode").asString()).isEqualTo("UNREADABLE_DOCUMENT");
        verify(gemini, never()).extract(anyString());
    }

    @Test
    void jobFailsWithAGenericMessageWhenTheAiServiceFails() {
        when(gemini.extract(anyString())).thenThrow(new AiServiceException("Gemini request failed: 401 secret detail"));

        ResponseEntity<JsonNode> accepted = api.upload("cv.pdf", CvFixtures.pdf("Valieria Karlova"));
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("FAILED");
        assertThat(finished.get("errorCode").asString()).isEqualTo("AI_SERVICE_ERROR");
        assertThat(finished.get("errorMessage").asString()).doesNotContain("secret");
    }

    @Test
    void secondImportWithTheSameEmailUpdatesTheExistingCv() {
        String email = uniqueEmail();
        when(gemini.extract(anyString()))
                .thenReturn(cv(email, "first summary", "Java Developer"))
                .thenReturn(cv(email, "second summary", "Senior Java Developer"));

        JsonNode first = api.awaitFinished(api.upload("cv.pdf", CvFixtures.pdf("first")).getBody().get("id").asString());
        JsonNode second = api.awaitFinished(api.upload("cv.pdf", CvFixtures.pdf("second")).getBody().get("id").asString());

        assertThat(first.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(second.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(second.get("cvId").asLong()).isEqualTo(first.get("cvId").asLong());
        JsonNode stored = api.cv(second.get("cvId").asLong()).getBody();
        assertThat(stored.get("summary").asString()).isEqualTo("second summary");
        assertThat(stored.get("experience")).hasSize(1);
        assertThat(stored.get("experience").get(0).get("position").asString()).isEqualTo("Senior Java Developer");
    }

    @Test
    void unsupportedFileIsRejectedImmediately() {
        ResponseEntity<JsonNode> response = api.upload("cv.txt", new byte[] {1});

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(gemini, never()).extract(anyString());
    }

    @Test
    void unknownJobReturns404() {
        assertThat(api.importStatus(UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(404);
    }
}
