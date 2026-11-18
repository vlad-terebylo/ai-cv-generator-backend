package org.example.aicvgenerator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.JobStatus;
import org.example.aicvgenerator.service.model.EntityType;
import org.example.aicvgenerator.exception.InvalidIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

class JobDtoMapperTest {

    private final JobDtoMapper mapper = Mappers.getMapper(JobDtoMapper.class);

    @Test
    void mapsACompletedImportToAReferenceToTheCv() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-11-11T10:00:00Z");
        Instant updated = Instant.parse("2026-11-11T10:00:05Z");
        Job domainJob = Job.builder().id(id).status(JobStatus.COMPLETED).label("cv.pdf")
                .entityType(EntityType.CV).entityId("42").createdAt(created).updatedAt(updated).build();

        JobResponse job = mapper.toResponse(domainJob);

        assertThat(job).isEqualTo(new JobResponse(id.toString(), org.example.aicvgenerator.controller.dto.JobStatus.COMPLETED, org.example.aicvgenerator.controller.dto.EntityType.CV, "42", null, null,
                created, updated));
    }

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void everyDomainStatusHasTheSameNamedResponseStatus(JobStatus status) {
        Job domainJob = Job.builder().id(UUID.randomUUID()).status(status).build();

        assertThat(mapper.toResponse(domainJob).status().name()).isEqualTo(status.name());
    }

    @Test
    void pointsAtNothingUntilTheImportIsCompleted() {
        Job domainJob = Job.builder().id(UUID.randomUUID()).status(JobStatus.PENDING)
                .label("cv.pdf").build();

        JobResponse job = mapper.toResponse(domainJob);

        assertThat(job.entityType()).isNull();
        assertThat(job.entityId()).isNull();
    }

    @Test
    void carriesTheErrorOfAFailedImportWithoutAReference() {
        Job domainJob = Job.builder().id(UUID.randomUUID()).status(JobStatus.FAILED)
                .errorCode("NOT_A_CV").errorMessage("The uploaded document is not a CV").build();

        JobResponse job = mapper.toResponse(domainJob);

        assertThat(job.status()).isEqualTo(org.example.aicvgenerator.controller.dto.JobStatus.FAILED);
        assertThat(job.errorCode()).isEqualTo("NOT_A_CV");
        assertThat(job.errorMessage()).isEqualTo("The uploaded document is not a CV");
        assertThat(job.entityType()).isNull();
        assertThat(job.entityId()).isNull();
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void parsesAJobIdAndRejectsAnythingElse() {
        UUID id = UUID.randomUUID();

        assertThat(mapper.toJobId(id.toString())).isEqualTo(id);
        assertThatThrownBy(() -> mapper.toJobId("not-a-uuid")).isInstanceOf(InvalidIdException.class);
    }
}
