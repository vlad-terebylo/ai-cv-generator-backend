package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.aicvgenerator.exception.CvNotFoundException;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.mapper.CvEntityMapper;
import org.example.aicvgenerator.mapper.CvExtractionMapper;
import org.example.aicvgenerator.repository.CvRepository;
import org.example.aicvgenerator.repository.ExperienceRepository;
import org.example.aicvgenerator.repository.UpsertResult;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvExtraction;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CvServiceTest {

    @Mock DocumentTextExtractor textExtractor;
    @Mock CvAnalyzer cvAnalyzer;
    @Mock CvRepository cvRepository;
    @Mock ExperienceRepository experienceRepository;

    CvService service;
    final CvDocument document = new CvDocument("cv.pdf", new byte[] {1});

    @BeforeEach
    void setUp() {
        service = new CvService(textExtractor, cvAnalyzer, cvRepository, experienceRepository,
                Mappers.getMapper(CvEntityMapper.class), Mappers.getMapper(CvExtractionMapper.class));
        lenient().when(textExtractor.extract(document)).thenReturn("cv text");
    }

    private CvExtraction extraction(boolean isCv, String email) {
        return new CvExtraction(isCv, "Valieria", "Karlova", email, "proactive",
                List.of(new CvExtraction.ExtractedExperience(2025, null, "Senior Java Developer", "Absolute")));
    }

    @Test
    void insertsNewCvWithNormalizedEmail() {
        when(cvAnalyzer.analyze("cv text")).thenReturn(extraction(true, " V.Karlova@Gmail.com "));
        when(cvRepository.upsertByEmail(any())).thenReturn(new UpsertResult(7L, true));

        CvSaveResult result = service.process(document);

        assertThat(result.created()).isTrue();
        assertThat(result.cv().getId()).isEqualTo(7L);
        assertThat(result.cv().getEmail()).isEqualTo("v.karlova@gmail.com");
        assertThat(result.cv().getSourceFileName()).isEqualTo("cv.pdf");
        verify(experienceRepository).replaceByCvId(eq(7L), anyList());
    }

    @Test
    void reportsAnExistingCvWithTheSameEmailAsUpdatedAndReplacesItsExperience() {
        when(cvAnalyzer.analyze("cv text")).thenReturn(extraction(true, "v.karlova@gmail.com"));
        when(cvRepository.upsertByEmail(any())).thenReturn(new UpsertResult(3L, false));

        CvSaveResult result = service.process(document);

        assertThat(result.created()).isFalse();
        assertThat(result.cv().getId()).isEqualTo(3L);
        verify(experienceRepository).replaceByCvId(eq(3L), anyList());
    }

    @Test
    void rejectsDocumentThatIsNotACv() {
        when(cvAnalyzer.analyze("cv text")).thenReturn(new CvExtraction(false, null, null, null, null, null));

        assertThatThrownBy(() -> service.process(document)).isInstanceOf(NotACvException.class);
        verify(cvRepository, never()).upsertByEmail(any());
    }

    @Test
    void rejectsCvWithoutEmail() {
        when(cvAnalyzer.analyze("cv text")).thenReturn(extraction(true, " "));

        assertThatThrownBy(() -> service.process(document)).isInstanceOf(NotACvException.class);
        verify(cvRepository, never()).upsertByEmail(any());
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(cvRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(CvNotFoundException.class);
    }
}
