package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.example.aicvgenerator.exception.CvImportNotFoundException;
import org.example.aicvgenerator.exception.ServerBusyException;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.mapper.CvImportEntityMapper;
import org.example.aicvgenerator.repository.InMemoryCvImportRepository;
import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvImport;
import org.example.aicvgenerator.service.model.CvImportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

@ExtendWith(MockitoExtension.class)
class CvImportServiceTest {

    @Mock TaskExecutor executor;
    @Mock CvImportProcessor processor;

    final InMemoryCvImportRepository repository = spy(new InMemoryCvImportRepository());
    final CvDocument document = new CvDocument("cv.pdf", new byte[] {1});
    CvImportService service;

    @BeforeEach
    void setUp() {
        service = new CvImportService(new DocumentTextExtractor(), repository,
                Mappers.getMapper(CvImportEntityMapper.class), processor, executor);
    }

    @Test
    void createsPendingJobAndQueuesTheDocument() {
        CvImport job = service.submit(document);

        assertThat(job.getId()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(CvImportStatus.PENDING);
        assertThat(job.getFileName()).isEqualTo("cv.pdf");
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(service.getById(job.getId()).getStatus()).isEqualTo(CvImportStatus.PENDING);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        verifyNoInteractions(processor);
        task.getValue().run();
        verify(processor).run(job.getId(), document);
    }

    @Test
    void rejectsUnsupportedExtensionWithoutCreatingAJob() {
        CvDocument text = new CvDocument("cv.txt", new byte[] {1});

        assertThatThrownBy(() -> service.submit(text)).isInstanceOf(UnsupportedDocumentException.class);

        verify(executor, never()).execute(any());
    }

    @Test
    void rejectsEmptyFileWithoutCreatingAJob() {
        CvDocument empty = new CvDocument("cv.pdf", new byte[0]);

        assertThatThrownBy(() -> service.submit(empty)).isInstanceOf(UnreadableDocumentException.class);

        verify(executor, never()).execute(any());
    }

    @Test
    void deletesTheJobAndThrowsWhenTheQueueIsFull() {
        doThrow(new TaskRejectedException("queue full")).when(executor).execute(any());

        assertThatThrownBy(() -> service.submit(document)).isInstanceOf(ServerBusyException.class);

        // the client never gets this job's id with the 503, so no unreachable record may remain
        ArgumentCaptor<CvImportEntity> inserted = ArgumentCaptor.forClass(CvImportEntity.class);
        verify(repository).insert(inserted.capture());
        UUID id = inserted.getValue().getId();
        verify(repository).delete(id);
        assertThat(repository.findById(id)).isEmpty();
        verifyNoInteractions(processor);
    }

    @Test
    void getByIdThrowsForUnknownJob() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID())).isInstanceOf(CvImportNotFoundException.class);
    }
}
