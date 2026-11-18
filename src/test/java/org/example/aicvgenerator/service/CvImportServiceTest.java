package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.example.aicvgenerator.service.model.EntityRef;
import org.example.aicvgenerator.service.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Only the CV-specific part: validation up front and the task that imports the CV. */
@ExtendWith(MockitoExtension.class)
class CvImportServiceTest {

    @Mock CvService cvService;
    @Mock JobService jobService;

    final CvDocument document = new CvDocument("cv.pdf", new byte[] {1});
    CvImportService service;

    @BeforeEach
    void setUp() {
        service = new CvImportService(new DocumentTextExtractor(), cvService, jobService);
    }

    @Test
    void submitsAJobLabelledWithTheFileName() {
        Job job = Job.builder().id(UUID.randomUUID()).build();
        when(jobService.submit(eq("cv.pdf"), any())).thenReturn(job);

        assertThat(service.submit(document)).isSameAs(job);
        verifyNoInteractions(cvService);
    }

    @Test
    void theSubmittedTaskImportsTheCvAndPointsAtIt() {
        ArgumentCaptor<JobTask> task = ArgumentCaptor.forClass(JobTask.class);
        service.submit(document);
        verify(jobService).submit(eq("cv.pdf"), task.capture());
        when(cvService.process(document)).thenReturn(new CvSaveResult(Cv.builder().id(42L).build(), true));

        EntityRef result = task.getValue().run();

        assertThat(result).isEqualTo(new EntityRef(EntityType.CV, "42"));
    }

    @Test
    void rejectsUnsupportedExtensionWithoutCreatingAJob() {
        CvDocument text = new CvDocument("cv.txt", new byte[] {1});

        assertThatThrownBy(() -> service.submit(text)).isInstanceOf(UnsupportedDocumentException.class);

        verify(jobService, never()).submit(any(), any());
    }

    @Test
    void rejectsEmptyFileWithoutCreatingAJob() {
        CvDocument empty = new CvDocument("cv.pdf", new byte[0]);

        assertThatThrownBy(() -> service.submit(empty)).isInstanceOf(UnreadableDocumentException.class);

        verify(jobService, never()).submit(any(), any());
    }
}
