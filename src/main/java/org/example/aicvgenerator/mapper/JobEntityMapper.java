package org.example.aicvgenerator.mapper;

import org.example.aicvgenerator.repository.entity.JobEntity;
import org.example.aicvgenerator.service.model.Job;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface JobEntityMapper {

    JobEntity toEntity(Job job);

    Job toDomain(JobEntity entity);
}
