package org.example.aicvgenerator.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.example.aicvgenerator.repository.entity.JobEntity;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.JobStatus;
import org.example.aicvgenerator.service.model.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

class JobEntityMapperTest {

    private final JobEntityMapper mapper = Mappers.getMapper(JobEntityMapper.class);

    @ParameterizedTest
    @EnumSource(JobStatus.class)
    void convertsEveryStatusBetweenEnumAndString(JobStatus status) {
        Job domain = Job.builder().id(UUID.randomUUID()).status(status).build();

        JobEntity entity = mapper.toEntity(domain);

        assertThat(entity.getStatus()).isEqualTo(status.name());
        assertThat(mapper.toDomain(entity).getStatus()).isEqualTo(status);
    }

    @Test
    void mapsAllFieldsBothWays() {
        Instant created = Instant.parse("2026-11-04T10:00:00Z");
        Instant updated = Instant.parse("2026-11-04T10:00:05Z");
        Job domain = Job.builder().id(UUID.randomUUID()).status(JobStatus.FAILED)
                .label("cv.pdf").entityType(EntityType.CV).entityId("3").errorCode("NOT_A_CV").errorMessage("nope")
                .createdAt(created).updatedAt(updated).build();

        Job roundTrip = mapper.toDomain(mapper.toEntity(domain));

        assertThat(roundTrip).isEqualTo(domain);
    }

    @Test
    void mapsNullsToNulls() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
