package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.aicvgenerator.exception.AiServiceException;
import org.example.aicvgenerator.exception.JobNotFoundException;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.exception.ServerBusyException;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.mapper.JobEntityMapper;
import org.example.aicvgenerator.repository.InMemoryJobRepository;
import org.example.aicvgenerator.repository.entity.JobEntity;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.JobStatus;
import org.example.aicvgenerator.service.model.EntityRef;
import org.example.aicvgenerator.service.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/** The job machinery is tested with fake tasks: nothing here knows about CVs. */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock TaskExecutor executor;

    final InMemoryJobRepository repository = spy(new InMemoryJobRepository());
    JobService service;

    @BeforeEach
    void setUp() {
        service = new JobService(repository, Mappers.getMapper(JobEntityMapper.class), executor);
    }

    private UUID insertPendingJob() {
        UUID id = UUID.randomUUID();
        repository.insert(JobEntity.builder().id(id).status("PENDING").label("cv.pdf").build());
        return id;
    }

    private JobEntity job(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    @Test
    void submitCreatesAPendingJobAndQueuesTheTaskWithoutRunningIt() {
        AtomicBoolean ran = new AtomicBoolean();
        JobTask task = () -> {
            ran.set(true);
            return new EntityRef(EntityType.CV, "1");
        };

        Job job = service.submit("cv.pdf", task);

        assertThat(job.getId()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getLabel()).isEqualTo("cv.pdf");
        assertThat(job.getCreatedAt()).isNotNull();
        assertThat(service.getById(job.getId()).getStatus()).isEqualTo(JobStatus.PENDING);
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(queued.capture());
        assertThat(ran).isFalse();

        queued.getValue().run();

        assertThat(ran).isTrue();
        assertThat(service.getById(job.getId()).getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void submitDeletesTheJobAndThrowsWhenTheQueueIsFull() {
        doThrow(new TaskRejectedException("queue full")).when(executor).execute(any());

        assertThatThrownBy(() -> service.submit("cv.pdf", () -> null)).isInstanceOf(ServerBusyException.class);

        // the client never gets this job's id with the 503, so no unreachable record may remain
        ArgumentCaptor<JobEntity> inserted = ArgumentCaptor.forClass(JobEntity.class);
        verify(repository).insert(inserted.capture());
        UUID id = inserted.getValue().getId();
        verify(repository).delete(id);
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void getByIdThrowsForUnknownJob() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID())).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void completesTheJobWithTheReferenceReturnedByTheTask() {
        UUID id = insertPendingJob();
        List<String> statusDuringProcessing = new ArrayList<>();

        service.run(id, () -> {
            statusDuringProcessing.add(job(id).getStatus());
            return new EntityRef(EntityType.CV, "42");
        });

        assertThat(statusDuringProcessing).containsExactly("PROCESSING");
        assertThat(job(id).getStatus()).isEqualTo("COMPLETED");
        assertThat(job(id).getEntityType()).isEqualTo("CV");
        assertThat(job(id).getEntityId()).isEqualTo("42");
        assertThat(job(id).getErrorCode()).isNull();
    }

    @Test
    void aJobFailureFailsTheJobWithItsOwnCodeAndMessage() {
        UUID id = insertPendingJob();

        service.run(id, () -> {
            throw new NotACvException("The uploaded document is not a CV");
        });

        assertFailed(id, "NOT_A_CV", "The uploaded document is not a CV");
    }

    @Test
    void unreadableAndUnsupportedDocumentsFailTheJobAsUnreadable() {
        UUID unreadable = insertPendingJob();
        UUID unsupported = insertPendingJob();

        service.run(unreadable, () -> {
            throw new UnreadableDocumentException("No text could be extracted");
        });
        service.run(unsupported, () -> {
            throw new UnsupportedDocumentException("Only .pdf and .docx files are supported");
        });

        assertFailed(unreadable, "UNREADABLE_DOCUMENT", "No text could be extracted");
        assertFailed(unsupported, "UNREADABLE_DOCUMENT", "Only .pdf and .docx files are supported");
    }

    @Test
    void anAiFailureFailsTheJobWithoutLeakingDetails() {
        UUID id = insertPendingJob();

        service.run(id, () -> {
            throw new AiServiceException("Gemini request failed: 401 secret detail");
        });

        assertFailed(id, "AI_SERVICE_ERROR", "The AI service could not process the document");
    }

    @Test
    void anyOtherExceptionFailsTheJobWithAGenericMessage() {
        UUID id = insertPendingJob();

        service.run(id, () -> {
            throw new IllegalStateException("internal detail");
        });

        assertFailed(id, "INTERNAL_ERROR", "The job could not be processed");
    }

    @Test
    void aJvmErrorIsRecordedOnTheJobSoItCannotStayInProcessingAndThenRethrown() {
        UUID id = insertPendingJob();

        assertThatThrownBy(() -> service.run(id, () -> {
            throw new NoClassDefFoundError("org/apache/pdfbox/Missing");
        })).isInstanceOf(NoClassDefFoundError.class);

        assertFailed(id, "INTERNAL_ERROR", "The job could not be processed");
    }

    @Test
    void neverThrowsEvenWhenTheJobIsMissing() {
        UUID unknown = UUID.randomUUID();
        AtomicBoolean ran = new AtomicBoolean();

        service.run(unknown, () -> {
            ran.set(true);
            return new EntityRef(EntityType.CV, "1");
        });

        assertThat(ran).as("the task is not started for a job that does not exist").isFalse();
        verify(repository, never()).update(any());
        assertThat(repository.findById(unknown)).isEmpty();
    }

    private void assertFailed(UUID id, String code, String message) {
        assertThat(job(id).getStatus()).isEqualTo("FAILED");
        assertThat(job(id).getErrorCode()).isEqualTo(code);
        assertThat(job(id).getErrorMessage()).isEqualTo(message);
        assertThat(job(id).getEntityId()).isNull();
    }
}
