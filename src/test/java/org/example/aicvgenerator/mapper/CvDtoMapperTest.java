package org.example.aicvgenerator.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.aicvgenerator.exception.InvalidIdException;
import org.example.aicvgenerator.service.model.Cv;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CvDtoMapperTest {

    private final CvDtoMapper mapper = Mappers.getMapper(CvDtoMapper.class);

    @Test
    void exposesTheCvIdAsText() {
        assertThat(mapper.toResponse(Cv.builder().id(7L).build()).id()).isEqualTo("7");
        assertThat(mapper.toResponse(Cv.builder().build()).id()).isNull();
    }

    @Test
    void parsesACvIdAndRejectsAnythingElse() {
        assertThat(mapper.toCvId("7")).isEqualTo(7L);
        assertThatThrownBy(() -> mapper.toCvId("abc")).isInstanceOf(InvalidIdException.class)
                .hasMessageContaining("abc");
        assertThatThrownBy(() -> mapper.toCvId("")).isInstanceOf(InvalidIdException.class);
    }
}
