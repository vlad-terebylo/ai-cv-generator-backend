package org.example.aicvgenerator.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.service.model.CvImport;
import org.example.aicvgenerator.service.model.CvImportStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CvImportDtoMapperTest {

    private final CvImportDtoMapper mapper = Mappers.getMapper(CvImportDtoMapper.class);

    @Test
    void mapsACompletedImportToAGenericJob() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-11T10:00:00Z");
        Instant updated = Instant.parse("2026-11-11T10:00:05Z");
        CvImport cvImport = CvImport.builder().id(id).status(CvImportStatus.COMPLETED).fileName("cv.pdf")
                .cvId(42L).createdAt(created).updatedAt(updated).build();

        JobResponse job = mapper.toResponse(cvImport);

        assertThat(job).isEqualTo(new JobResponse(id, "COMPLETED", "cv.pdf",
                new JobResponse.Result("CV", 42L), null, null, created, updated));
    }

    @Test
    void hasNoResultUntilTheImportIsCompleted() {
        CvImport cvImport = CvImport.builder().id(UUID.randomUUID()).status(CvImportStatus.PENDING)
                .fileName("cv.pdf").build();

        assertThat(mapper.toResponse(cvImport).result()).isNull();
    }

    @Test
    void carriesTheErrorOfAFailedImportWithoutAResult() {
        CvImport cvImport = CvImport.builder().id(UUID.randomUUID()).status(CvImportStatus.FAILED)
                .errorCode("NOT_A_CV").errorMessage("The uploaded document is not a CV").build();

        JobResponse job = mapper.toResponse(cvImport);

        assertThat(job.status()).isEqualTo("FAILED");
        assertThat(job.errorCode()).isEqualTo("NOT_A_CV");
        assertThat(job.errorMessage()).isEqualTo("The uploaded document is not a CV");
        assertThat(job.result()).isNull();
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
