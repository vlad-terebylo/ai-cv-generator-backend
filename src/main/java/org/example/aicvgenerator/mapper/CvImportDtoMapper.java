package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.controller.dto.EntityType;
import org.example.aicvgenerator.controller.dto.JobResponse;
import org.example.aicvgenerator.service.model.CvImport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface CvImportDtoMapper {

    @Mapping(target = "entityType", source = "cvId", qualifiedByName = "cvEntityType")
    @Mapping(target = "entityId", source = "cvId")
    JobResponse toResponse(CvImport cvImport);

    /** A CV import produces a CV once it has an id; before that there is nothing to point at. */
    @Named("cvEntityType")
    default EntityType cvEntityType(Long cvId) {
        return cvId == null ? null : EntityType.CV;
    }
}
