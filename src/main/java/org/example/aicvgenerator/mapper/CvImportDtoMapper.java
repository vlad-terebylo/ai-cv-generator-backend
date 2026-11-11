package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.service.model.CvImport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CvImportDtoMapper {

    String RESULT_TYPE = "CV";

    @Mapping(target = "name", source = "fileName")
    @Mapping(target = "result", source = "cvId")
    JobResponse toResponse(CvImport cvImport);

    default JobResponse.Result toResult(Long cvId) {
        return cvId == null ? null : new JobResponse.Result(RESULT_TYPE, cvId);
    }
}
