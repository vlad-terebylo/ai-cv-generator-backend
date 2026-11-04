package org.example.aicvgenerator.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.example.aicvgenerator.service.model.CvImport;
import org.example.aicvgenerator.service.model.CvImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

class CvImportEntityMapperTest {

    private final CvImportEntityMapper mapper = Mappers.getMapper(CvImportEntityMapper.class);

    @ParameterizedTest
    @EnumSource(CvImportStatus.class)
    void convertsEveryStatusBetweenEnumAndString(CvImportStatus status) {
        CvImport domain = CvImport.builder().id(UUID.randomUUID()).status(status).build();

        CvImportEntity entity = mapper.toEntity(domain);

        assertThat(entity.getStatus()).isEqualTo(status.name());
        assertThat(mapper.toDomain(entity).getStatus()).isEqualTo(status);
    }

    @Test
    void mapsAllFieldsBothWays() {
        Instant created = Instant.parse("2026-11-04T10:00:00Z");
        Instant updated = Instant.parse("2026-11-04T10:00:05Z");
        CvImport domain = CvImport.builder().id(UUID.randomUUID()).status(CvImportStatus.FAILED)
                .fileName("cv.pdf").cvId(3L).errorCode("NOT_A_CV").errorMessage("nope")
                .createdAt(created).updatedAt(updated).build();

        CvImport roundTrip = mapper.toDomain(mapper.toEntity(domain));

        assertThat(roundTrip).isEqualTo(domain);
    }

    @Test
    void mapsNullsToNulls() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
