package org.example.aicvgenerator.mapper;

import java.util.UUID;

import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.exception.InvalidIdException;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobDtoMapper {

    JobResponse toResponse(Job job);

    /** The client-facing form of a job id. */
    default String idAsText(UUID id) {
        return id == null ? null : id.toString();
    }

    /** @throws InvalidIdException if the text is not a job id */
    default UUID toJobId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidIdException(id);
        }
    }
}
