package org.example.aicvgenerator.service;

import org.example.aicvgenerator.exception.ServerBusyException;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.example.aicvgenerator.service.model.EntityRef;
import org.example.aicvgenerator.service.model.EntityType;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Accepts uploads quickly and hands the slow parsing to the background {@link JobService}. */
@Service
@RequiredArgsConstructor
public class CvImportService {

    private final DocumentTextExtractor textExtractor;
    private final CvService cvService;
    private final JobService jobService;

    /**
     * Rejects unsupported files at once, then creates a PENDING job that imports the CV in the background.
     *
     * @throws ServerBusyException if the queue is full
     */
    public Job submit(CvDocument document) {
        textExtractor.requireSupported(document);
        return jobService.submit(document.fileName(), () -> importCv(document));
    }

    private EntityRef importCv(CvDocument document) {
        CvSaveResult result = cvService.process(document);
        return new EntityRef(EntityType.CV, String.valueOf(result.cv().getId()));
    }
}
