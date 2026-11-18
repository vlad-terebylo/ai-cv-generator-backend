package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.example.aicvgenerator.mapper.CvEntityMapper;
import org.example.aicvgenerator.mapper.CvExtractionMapper;
import org.example.aicvgenerator.repository.InMemoryCvRepository;
import org.example.aicvgenerator.repository.InMemoryExperienceRepository;
import org.example.aicvgenerator.repository.entity.CvEntity;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvExtraction;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** Workers import in parallel, so saving the same email twice at once must not fail. */
class CvServiceConcurrencyTest {

    /** Pauses after the email lookup: a non-atomic upsert would let both workers insert in that window. */
    private static class SlowLookupCvRepository extends InMemoryCvRepository {
        @Override
        public Optional<CvEntity> findByEmail(String email) {
            Optional<CvEntity> found = super.findByEmail(email);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return found;
        }
    }

    @Test
    void twoWorkersImportingTheSameEmailBothSucceed() throws Exception {
        DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
        CvAnalyzer analyzer = mock(CvAnalyzer.class);
        CvDocument document = new CvDocument("cv.pdf", new byte[] {1});
        when(extractor.extract(document)).thenReturn("cv text");
        when(analyzer.analyze("cv text")).thenReturn(new CvExtraction(true, "Valieria", "Karlova",
                "v.karlova@gmail.com", "proactive",
                List.of(new CvExtraction.ExtractedExperience(2025, null, "Senior Java Developer", "Absolute"))));
        CvService service = new CvService(extractor, analyzer, new SlowLookupCvRepository(),
                new InMemoryExperienceRepository(), Mappers.getMapper(CvEntityMapper.class),
                Mappers.getMapper(CvExtractionMapper.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CvSaveResult>> results = List.of(
                    pool.submit(() -> { start.await(); return service.process(document); }),
                    pool.submit(() -> { start.await(); return service.process(document); }));
            start.countDown();

            CvSaveResult first = results.get(0).get();
            CvSaveResult second = results.get(1).get();

            assertThat(first.cv().getId()).isEqualTo(second.cv().getId());
            assertThat(first.created() ^ second.created()).as("exactly one of them created the CV").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
