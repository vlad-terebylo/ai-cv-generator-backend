package org.example.aicvgenerator.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Multipart limits are enforced by the servlet container, so MockMvc cannot test them: use a real server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadSizeLimitTest {

    private static final int TWO_MB = 2 * 1024 * 1024;

    @LocalServerPort
    int port;

    private ResponseEntity<String> upload(String fileName, int size) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[size]) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return RestClient.create(String.format("http://localhost:%d", port)).post()
                .uri("/api/v1/cvs")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(String.class);
    }

    @Test
    void rejectsFileOverTwoMegabytes() {
        ResponseEntity<String> response = upload("cv.pdf", TWO_MB + 1024);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("2MB");
    }

    @Test
    void doesNotRejectFileJustUnderTwoMegabytesForSize() {
        // ".txt" is rejected for its type (400), which proves the size check let the file through
        ResponseEntity<String> response = upload("cv.txt", TWO_MB - 1024);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("Only .pdf and .docx");
    }
}
