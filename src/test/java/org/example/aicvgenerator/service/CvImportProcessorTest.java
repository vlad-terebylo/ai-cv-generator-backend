package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.example.aicvgenerator.exception.AiServiceException;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.mapper.CvImportEntityMapper;
import org.example.aicvgenerator.repository.InMemoryCvImportRepository;
import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CvImportProcessorTest {

    @Mock CvService cvService;

    final InMemoryCvImportRepository repository = new InMemoryCvImportRepository();
    final CvDocument document = new CvDocument("cv.pdf", new byte[] {1});
    UUID jobId;
    CvImportProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CvImportProcessor(cvService, repository, Mappers.getMapper(CvImportEntityMapper.class));
        jobId = UUID.randomUUID();
        repository.insert(CvImportEntity.builder().id(jobId).status("PENDING").fileName("cv.pdf").build());
    }

    private CvImportEntity job() {
        return repository.findById(jobId).orElseThrow();
    }

    @Test
    void completesJobWithTheStoredCvId() {
        List<String> statusDuringProcessing = new ArrayList<>();
        when(cvService.process(document)).thenAnswer(invocation -> {
            statusDuringProcessing.add(job().getStatus());
            return new CvSaveResult(Cv.builder().id(42L).build(), true);
        });

        processor.run(jobId, document);

        assertThat(statusDuringProcessing).containsExactly("PROCESSING");
        assertThat(job().getStatus()).isEqualTo("COMPLETED");
        assertThat(job().getCvId()).isEqualTo(42L);
        assertThat(job().getErrorCode()).isNull();
    }

    @Test
    void notACvFailsTheJob() {
        when(cvService.process(document)).thenThrow(new NotACvException("The uploaded document is not a CV"));

        processor.run(jobId, document);

        assertFailed("NOT_A_CV", "The uploaded document is not a CV");
    }

    @Test
    void unreadableDocumentFailsTheJob() {
        when(cvService.process(document)).thenThrow(new UnreadableDocumentException("No text could be extracted"));

        processor.run(jobId, document);

        assertFailed("UNREADABLE_DOCUMENT", "No text could be extracted");
    }

    @Test
    void unsupportedDocumentFailsTheJobAsUnreadable() {
        when(cvService.process(document)).thenThrow(new UnsupportedDocumentException("Only .pdf and .docx files are supported"));

        processor.run(jobId, document);

        assertFailed("UNREADABLE_DOCUMENT", "Only .pdf and .docx files are supported");
    }

    @Test
    void aiFailureFailsTheJobWithoutLeakingDetails() {
        when(cvService.process(document)).thenThrow(new AiServiceException("Gemini request failed: 401 secret detail"));

        processor.run(jobId, document);

        assertFailed("AI_SERVICE_ERROR", "The AI service could not process the document");
    }

    @Test
    void unexpectedFailureFailsTheJobWithAGenericMessage() {
        when(cvService.process(any())).thenThrow(new IllegalStateException("internal detail"));

        processor.run(jobId, document);

        assertFailed("INTERNAL_ERROR", "The document could not be processed");
    }

    @Test
    void aJvmErrorIsRecordedOnTheJobSoItCannotStayInProcessingAndThenRethrown() {
        when(cvService.process(any())).thenThrow(new NoClassDefFoundError("org/apache/pdfbox/Missing"));

        assertThatThrownBy(() -> processor.run(jobId, document)).isInstanceOf(NoClassDefFoundError.class);

        assertFailed("INTERNAL_ERROR", "The document could not be processed");
    }

    @Test
    void neverThrowsEvenWhenTheJobIsMissing() {
        UUID unknown = UUID.randomUUID();

        processor.run(unknown, document);

        assertThat(repository.findById(unknown)).isEmpty();
    }

    private void assertFailed(String code, String message) {
        assertThat(job().getStatus()).isEqualTo("FAILED");
        assertThat(job().getErrorCode()).isEqualTo(code);
        assertThat(job().getErrorMessage()).isEqualTo(message);
        assertThat(job().getCvId()).isNull();
    }
}
