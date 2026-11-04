package org.example.aicvgenerator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.example.aicvgenerator.repository.entity.CvImportEntity;
import org.junit.jupiter.api.Test;

class InMemoryCvImportRepositoryTest extends CvImportRepositoryContractTest {

    private final InMemoryCvImportRepository cvImportRepository = new InMemoryCvImportRepository();

    @Override
    CvImportRepository cvImportRepository() {
        return cvImportRepository;
    }

    @Test
    void returnedEntitiesAreCopies() {
        UUID id = UUID.randomUUID();
        cvImportRepository.insert(CvImportEntity.builder().id(id).status("PENDING").fileName("cv.pdf").build());

        cvImportRepository.findById(id).orElseThrow().setStatus("mutated");

        assertThat(cvImportRepository.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
    }
}
