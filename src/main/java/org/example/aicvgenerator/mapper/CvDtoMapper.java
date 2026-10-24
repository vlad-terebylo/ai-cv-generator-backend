package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.controller.dto.CvResponse;
import org.example.aicvgenerator.controller.dto.ExperienceResponse;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.Experience;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CvDtoMapper {

    CvResponse toResponse(Cv cv);

    ExperienceResponse toResponse(Experience experience);
}
