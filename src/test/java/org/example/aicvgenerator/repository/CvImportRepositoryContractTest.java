package org.example.aicvgenerator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.junit.jupiter.api.Test;

/** Behaviour every import-job repository implementation (currently in-memory) must share. */
abstract class CvImportRepositoryContractTest {

    abstract CvImportRepository cvImportRepository();

    private CvImportEntity job() {
        return CvImportEntity.builder().id(UUID.randomUUID()).status("PENDING").fileName("cv.pdf").build();
    }

    @Test
    void insertsAndFindsById() {
        CvImportEntity job = job();

        cvImportRepository().insert(job);

        assertThat(cvImportRepository().findById(job.getId())).get().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(job.getId());
            assertThat(found.getStatus()).isEqualTo("PENDING");
            assertThat(found.getFileName()).isEqualTo("cv.pdf");
            assertThat(found.getCvId()).isNull();
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(cvImportRepository().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateStoresOutcomeAndBumpsUpdatedAt() throws InterruptedException {
        CvImportEntity job = job();
        cvImportRepository().insert(job);
        CvImportEntity inserted = cvImportRepository().findById(job.getId()).orElseThrow();
        Thread.sleep(5);

        cvImportRepository().update(inserted.toBuilder().status("COMPLETED").cvId(7L).build());

        CvImportEntity completed = cvImportRepository().findById(job.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getCvId()).isEqualTo(7L);
        assertThat(completed.getCreatedAt()).isEqualTo(inserted.getCreatedAt());
        assertThat(completed.getUpdatedAt()).isAfter(inserted.getUpdatedAt());
    }

    @Test
    void updateStoresFailureDetails() {
        CvImportEntity job = job();
        cvImportRepository().insert(job);

        cvImportRepository().update(job.toBuilder().status("FAILED").errorCode("NOT_A_CV")
                .errorMessage("The uploaded document is not a CV").build());

        assertThat(cvImportRepository().findById(job.getId())).get().satisfies(found -> {
            assertThat(found.getStatus()).isEqualTo("FAILED");
            assertThat(found.getErrorCode()).isEqualTo("NOT_A_CV");
            assertThat(found.getErrorMessage()).isEqualTo("The uploaded document is not a CV");
        });
    }

    @Test
    void updateOfUnknownIdIsIgnored() {
        CvImportEntity unknown = job();

        cvImportRepository().update(unknown);

        assertThat(cvImportRepository().findById(unknown.getId())).isEmpty();
    }

    @Test
    void updatingOneJobLeavesOthersUntouched() {
        CvImportEntity first = job();
        CvImportEntity second = job();
        cvImportRepository().insert(first);
        cvImportRepository().insert(second);

        cvImportRepository().update(first.toBuilder().status("PROCESSING").build());

        assertThat(cvImportRepository().findById(second.getId())).get()
                .extracting(CvImportEntity::getStatus).isEqualTo("PENDING");
    }
}
