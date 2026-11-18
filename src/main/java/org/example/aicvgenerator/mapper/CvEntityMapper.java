package org.example.aicvgenerator.mapper;

import java.util.List;

import org.example.aicvgenerator.repository.entity.CvEntity;
import org.example.aicvgenerator.repository.entity.ExperienceEntity;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.Experience;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CvEntityMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CvEntity toEntity(Cv cv);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cvId", source = "cvId")
    ExperienceEntity toEntity(Experience experience, Long cvId);

    default List<ExperienceEntity> toExperienceEntities(List<Experience> experience, Long cvId) {
        return experience == null ? List.of() : experience.stream().map(e -> toEntity(e, cvId)).toList();
    }

    Experience toDomain(ExperienceEntity entity);

    @Mapping(target = "id", source = "cv.id")
    @Mapping(target = "experience", source = "experience")
    Cv toDomain(CvEntity cv, List<ExperienceEntity> experience);
}
