package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.client.CvExtraction;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.Experience;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CvExtractionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sourceFileName", ignore = true)
    Cv toDomain(CvExtraction extraction);

    Experience toDomain(CvExtraction.ExtractedExperience experience);
}
