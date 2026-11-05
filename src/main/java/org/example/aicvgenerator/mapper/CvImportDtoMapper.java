package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.controller.dto.CvImportResponse;
import org.example.aicvgenerator.service.model.CvImport;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CvImportDtoMapper {

    CvImportResponse toResponse(CvImport cvImport);
}
