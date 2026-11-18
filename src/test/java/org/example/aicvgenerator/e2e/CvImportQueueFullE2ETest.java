package org.example.aicvgenerator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.example.aicvgenerator.client.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;

/** A pool of one thread with a queue of one: the third concurrent upload must be turned away. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"cv.import.pool-size=1", "cv.import.queue-capacity=1"})
class CvImportQueueFullE2ETest {

    @LocalServerPort
    int port;

    @MockitoBean
    GeminiClient gemini;

    @Test
    void thirdUploadGets503WhileTheFirstTwoStillComplete() throws Exception {
        CountDownLatch geminiMayFinish = new CountDownLatch(1);
        when(gemini.generateJson(anyString(), anyMap())).thenAnswer(invocation -> {
            geminiMayFinish.await();
            return GeminiAnswers.cv(String.format("%s@example.com", UUID.randomUUID()), "summary", "Java Developer");
        });
        CvImportApi api = new CvImportApi(port);

        try {
            ResponseEntity<JsonNode> first = api.upload("cv.pdf", CvFixtures.pdf("first"));    // runs on the only worker
            ResponseEntity<JsonNode> second = api.upload("cv.pdf", CvFixtures.pdf("second"));  // waits in the queue
            ResponseEntity<JsonNode> third = api.upload("cv.pdf", CvFixtures.pdf("third"));    // nowhere to go

            assertThat(first.getStatusCode().value()).isEqualTo(202);
            assertThat(second.getStatusCode().value()).isEqualTo(202);
            assertThat(third.getStatusCode().value()).isEqualTo(503);
            assertThat(third.getBody().get("detail").asString()).contains("busy");

            geminiMayFinish.countDown();

            assertThat(api.awaitFinished(first.getBody().get("id").asString()).get("status").asString())
                    .isEqualTo("COMPLETED");
            assertThat(api.awaitFinished(second.getBody().get("id").asString()).get("status").asString())
                    .isEqualTo("COMPLETED");
        } finally {
            geminiMayFinish.countDown();
        }
    }
}
