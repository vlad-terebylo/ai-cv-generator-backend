package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.example.aicvgenerator.service.model.CvImport;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CvImportEntityMapper {

    CvImportEntity toEntity(CvImport cvImport);

    CvImport toDomain(CvImportEntity entity);
}
