package org.example.aicvgenerator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.example.aicvgenerator.repository.entity.JobEntity;
import org.junit.jupiter.api.Test;

/** Behaviour every import-job repository implementation (currently in-memory) must share. */
abstract class JobRepositoryContractTest {

    abstract JobRepository jobRepository();

    private JobEntity job() {
        return JobEntity.builder().id(UUID.randomUUID()).status("PENDING").label("cv.pdf").build();
    }

    @Test
    void insertsAndFindsById() {
        JobEntity job = job();

        jobRepository().insert(job);

        assertThat(jobRepository().findById(job.getId())).get().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(job.getId());
            assertThat(found.getStatus()).isEqualTo("PENDING");
            assertThat(found.getLabel()).isEqualTo("cv.pdf");
            assertThat(found.getEntityId()).isNull();
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(jobRepository().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateStoresOutcomeAndBumpsUpdatedAt() throws InterruptedException {
        JobEntity job = job();
        jobRepository().insert(job);
        JobEntity inserted = jobRepository().findById(job.getId()).orElseThrow();
        Thread.sleep(5);

        jobRepository().update(inserted.toBuilder().status("COMPLETED").entityType("CV").entityId("7").build());

        JobEntity completed = jobRepository().findById(job.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getEntityType()).isEqualTo("CV");
        assertThat(completed.getEntityId()).isEqualTo("7");
        assertThat(completed.getCreatedAt()).isEqualTo(inserted.getCreatedAt());
        assertThat(completed.getUpdatedAt()).isAfter(inserted.getUpdatedAt());
    }

    @Test
    void updateStoresFailureDetails() {
        JobEntity job = job();
        jobRepository().insert(job);

        jobRepository().update(job.toBuilder().status("FAILED").errorCode("NOT_A_CV")
                .errorMessage("The uploaded document is not a CV").build());

        assertThat(jobRepository().findById(job.getId())).get().satisfies(found -> {
            assertThat(found.getStatus()).isEqualTo("FAILED");
            assertThat(found.getErrorCode()).isEqualTo("NOT_A_CV");
            assertThat(found.getErrorMessage()).isEqualTo("The uploaded document is not a CV");
        });
    }

    @Test
    void updateOfUnknownIdIsIgnored() {
        JobEntity unknown = job();

        jobRepository().update(unknown);

        assertThat(jobRepository().findById(unknown.getId())).isEmpty();
    }

    @Test
    void deleteRemovesOnlyThatJob() {
        JobEntity doomed = job();
        JobEntity kept = job();
        jobRepository().insert(doomed);
        jobRepository().insert(kept);

        jobRepository().delete(doomed.getId());

        assertThat(jobRepository().findById(doomed.getId())).isEmpty();
        assertThat(jobRepository().findById(kept.getId())).isPresent();
    }

    @Test
    void deleteOfUnknownIdIsIgnored() {
        JobEntity kept = job();
        jobRepository().insert(kept);

        jobRepository().delete(UUID.randomUUID());

        assertThat(jobRepository().findById(kept.getId())).isPresent();
    }

    @Test
    void updatingOneJobLeavesOthersUntouched() {
        JobEntity first = job();
        JobEntity second = job();
        jobRepository().insert(first);
        jobRepository().insert(second);

        jobRepository().update(first.toBuilder().status("PROCESSING").build());

        assertThat(jobRepository().findById(second.getId())).get()
                .extracting(JobEntity::getStatus).isEqualTo("PENDING");
    }
}
