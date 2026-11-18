package org.example.aicvgenerator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.example.aicvgenerator.repository.entity.JobEntity;
import org.junit.jupiter.api.Test;

class InMemoryJobRepositoryTest extends JobRepositoryContractTest {

    private final InMemoryJobRepository jobRepository = new InMemoryJobRepository();

    @Override
    JobRepository jobRepository() {
        return jobRepository;
    }

    @Test
    void returnedEntitiesAreCopies() {
        UUID id = UUID.randomUUID();
        jobRepository.insert(JobEntity.builder().id(id).status("PENDING").label("cv.pdf").build());

        jobRepository.findById(id).orElseThrow().setStatus("mutated");

        assertThat(jobRepository.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
    }
}
