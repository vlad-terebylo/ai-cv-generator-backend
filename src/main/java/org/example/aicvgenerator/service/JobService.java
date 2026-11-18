package org.example.aicvgenerator.service;

import static org.example.aicvgenerator.config.Timing.NANOS_PER_MILLISECOND;

import java.util.UUID;
import java.util.function.Consumer;

import org.example.aicvgenerator.exception.JobFailure;
import org.example.aicvgenerator.exception.JobNotFoundException;
import org.example.aicvgenerator.exception.ServerBusyException;
import org.example.aicvgenerator.mapper.JobEntityMapper;
import org.example.aicvgenerator.repository.JobRepository;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.JobStatus;
import org.example.aicvgenerator.service.model.EntityRef;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs any {@link JobTask} in the background and keeps its job row up to date. It has no knowledge of what a task
 * does; failures are classified through {@link JobFailure}.
 */
@Slf4j
@Service
public class JobService {

    static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    static final String INTERNAL_ERROR_MESSAGE = "The job could not be processed";

    private final JobRepository jobRepository;
    private final JobEntityMapper mapper;
    private final TaskExecutor executor;

    public JobService(JobRepository jobRepository, JobEntityMapper mapper,
                      @Qualifier("cvImportExecutor") TaskExecutor executor) {
        this.jobRepository = jobRepository;
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Creates a PENDING job and queues the task.
     *
     * @param label a short description of the input, kept on the job (currently the uploaded file name)
     * @throws ServerBusyException if the queue is full; the job is deleted again because the client never learns its id
     */
    public Job submit(String label, JobTask task) {
        UUID id = UUID.randomUUID();
        jobRepository.insert(mapper.toEntity(Job.builder()
                .id(id)
                .status(JobStatus.PENDING)
                .label(label)
                .build()));
        Job created = getById(id);
        try {
            executor.execute(() -> run(id, task));
        } catch (TaskRejectedException e) {
            jobRepository.delete(id);
            throw new ServerBusyException();
        }
        return created;
    }

    /** @throws JobNotFoundException if the id is unknown, which includes every job from before a restart */
    public Job getById(UUID id) {
        return jobRepository.findById(id)
                .map(mapper::toDomain)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    /**
     * Never throws an exception: any failure ends up on the job so the worker thread survives. A JVM {@link Error}
     * is recorded on the job as well, so the job cannot stay in PROCESSING, and then rethrown.
     */
    void run(UUID jobId, JobTask task) {
        long start = System.nanoTime();
        try {
            update(jobId, job -> job.setStatus(JobStatus.PROCESSING));
            EntityRef result = task.run();
            update(jobId, job -> {
                job.setStatus(JobStatus.COMPLETED);
                job.setEntityType(result.type());
                job.setEntityId(result.id());
            });
        } catch (RuntimeException e) {
            fail(jobId, e);
        } catch (Error e) {
            log.error("Fatal error for job {}", jobId, e);
            markFailed(jobId, INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);
            throw e;
        } finally {
            long tookMs = (System.nanoTime() - start) / NANOS_PER_MILLISECOND;
            log.info("Job {} took {} ms", jobId, tookMs);
        }
    }

    private void fail(UUID jobId, RuntimeException e) {
        if (e instanceof JobFailure failure) {
            if (e.getCause() != null) {
                log.warn("Job {} failed with {}", jobId, failure.errorCode(), e);
            } else {
                log.info("Job {} failed with {}: {}", jobId, failure.errorCode(), e.getMessage());
            }
            markFailed(jobId, failure.errorCode(), failure.safeMessage());
        } else {
            log.error("Unexpected failure for job {}", jobId, e);
            markFailed(jobId, INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);
        }
    }

    /** Records a failure on the job; logs instead of throwing if even that is impossible. */
    private void markFailed(UUID jobId, String errorCode, String errorMessage) {
        try {
            update(jobId, job -> {
                job.setStatus(JobStatus.FAILED);
                job.setErrorCode(errorCode);
                job.setErrorMessage(errorMessage);
            });
        } catch (RuntimeException e) {
            log.error("Could not record failure {} for job {}", errorCode, jobId, e);
        }
    }

    private void update(UUID jobId, Consumer<Job> change) {
        Job job = getById(jobId);
        change.accept(job);
        jobRepository.update(mapper.toEntity(job));
    }
}
