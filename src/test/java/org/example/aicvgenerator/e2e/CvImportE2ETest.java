package org.example.aicvgenerator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

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

    private static String uniqueEmail() {
        return String.format("%s@example.com", UUID.randomUUID());
    }

    @Test
    void postReturnsImmediatelyWhileGeminiIsStillWorking() throws Exception {
        String email = uniqueEmail();
        CountDownLatch geminiMayFinish = new CountDownLatch(1);
        when(gemini.generateJson(anyString(), anyMap())).thenAnswer(invocation -> {
            geminiMayFinish.await();
            return GeminiAnswers.cv(email, "proactive", "Senior Java Developer");
        });

        try {
            ResponseEntity<JsonNode> accepted = api.upload("cv.pdf", CvFixtures.pdf("Valieria Karlova, Java developer"));

            // Gemini is still blocked, yet the request already returned
            assertThat(geminiMayFinish.getCount()).isEqualTo(1);
            assertThat(accepted.getStatusCode().value()).isEqualTo(202);
            String jobId = accepted.getBody().get("id").asString();
            assertThat(accepted.getHeaders().getLocation()).hasToString(String.format("/api/v1/jobs/%s", jobId));
            assertThat(accepted.getBody().get("status").asString()).isEqualTo("PENDING");
            assertThat(api.importStatus(jobId).getBody().get("status").asString()).isIn("PENDING", "PROCESSING");

            geminiMayFinish.countDown();
            JsonNode finished = api.awaitFinished(jobId);

            assertThat(finished.get("status").asString()).isEqualTo("COMPLETED");
            assertThat(finished.get("entityType").asString()).isEqualTo("CV");
            ResponseEntity<JsonNode> stored = api.cv(CvImportApi.resultId(finished));
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
        when(gemini.generateJson(anyString(), anyMap())).thenReturn(GeminiAnswers.cv(email, "docx summary", "Java Developer"));

        ResponseEntity<JsonNode> accepted = api.upload("cv.docx", CvFixtures.docx("Valieria Karlova, Java developer"));
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(api.cv(CvImportApi.resultId(finished)).getBody().get("summary").asString()).isEqualTo("docx summary");
    }

    @Test
    void jobFailsWhenTheDocumentIsNotACv() {
        when(gemini.generateJson(anyString(), anyMap())).thenReturn(GeminiAnswers.notACv());

        ResponseEntity<JsonNode> accepted = api.upload("invoice.pdf", CvFixtures.pdf("Invoice 42, total 100 EUR"));
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("FAILED");
        assertThat(finished.get("errorCode").asString()).isEqualTo("NOT_A_CV");
        assertThat(finished.get("entityType").isNull()).isTrue();
        assertThat(finished.get("entityId").isNull()).isTrue();
    }

    @Test
    void jobFailsWhenTheFileHasNoReadableTextAndGeminiIsNeverCalled() {
        ResponseEntity<JsonNode> accepted = api.upload("broken.pdf", new byte[] {1, 2, 3});
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("FAILED");
        assertThat(finished.get("errorCode").asString()).isEqualTo("UNREADABLE_DOCUMENT");
        verify(gemini, never()).generateJson(anyString(), anyMap());
    }

    @Test
    void jobFailsWithAGenericMessageWhenTheAiServiceFails() {
        when(gemini.generateJson(anyString(), anyMap())).thenThrow(new AiServiceException("Gemini request failed: 401 secret detail"));

        ResponseEntity<JsonNode> accepted = api.upload("cv.pdf", CvFixtures.pdf("Valieria Karlova"));
        JsonNode finished = api.awaitFinished(accepted.getBody().get("id").asString());

        assertThat(finished.get("status").asString()).isEqualTo("FAILED");
        assertThat(finished.get("errorCode").asString()).isEqualTo("AI_SERVICE_ERROR");
        assertThat(finished.get("errorMessage").asString()).doesNotContain("secret");
    }

    @Test
    void secondImportWithTheSameEmailUpdatesTheExistingCv() {
        String email = uniqueEmail();
        when(gemini.generateJson(anyString(), anyMap()))
                .thenReturn(GeminiAnswers.cv(email, "first summary", "Java Developer"))
                .thenReturn(GeminiAnswers.cv(email, "second summary", "Senior Java Developer"));

        JsonNode first = api.awaitFinished(api.upload("cv.pdf", CvFixtures.pdf("first")).getBody().get("id").asString());
        JsonNode second = api.awaitFinished(api.upload("cv.pdf", CvFixtures.pdf("second")).getBody().get("id").asString());

        assertThat(first.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(second.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(CvImportApi.resultId(second)).isEqualTo(CvImportApi.resultId(first));
        JsonNode stored = api.cv(CvImportApi.resultId(second)).getBody();
        assertThat(stored.get("summary").asString()).isEqualTo("second summary");
        assertThat(stored.get("experience")).hasSize(1);
        assertThat(stored.get("experience").get(0).get("position").asString()).isEqualTo("Senior Java Developer");
    }

    @Test
    void unsupportedFileIsRejectedImmediately() {
        ResponseEntity<JsonNode> response = api.upload("cv.txt", new byte[] {1});

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(gemini, never()).generateJson(anyString(), anyMap());
    }

    @Test
    void unknownJobReturns404() {
        assertThat(api.importStatus(UUID.randomUUID().toString()).getStatusCode().value()).isEqualTo(404);
    }
}
