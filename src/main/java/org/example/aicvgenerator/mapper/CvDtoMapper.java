package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.controller.dto.CvResponse;
import org.example.aicvgenerator.controller.dto.ExperienceResponse;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.Experience;
import org.example.aicvgenerator.exception.InvalidIdException;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CvDtoMapper {

    CvResponse toResponse(Cv cv);

    /** The client-facing form of a CV id. */
    default String idAsText(Long id) {
        return id == null ? null : id.toString();
    }

    /** @throws InvalidIdException if the text is not a CV id */
    default long toCvId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new InvalidIdException(id);
        }
    }

    ExperienceResponse toResponse(Experience experience);
}
