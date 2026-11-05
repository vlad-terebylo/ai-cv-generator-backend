package org.example.aicvgenerator.service;

import java.util.UUID;
import java.util.function.Consumer;

import org.example.aicvgenerator.exception.AiServiceException;
import org.example.aicvgenerator.exception.CvImportNotFoundException;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.mapper.CvImportEntityMapper;
import org.example.aicvgenerator.repository.CvImportRepository;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvImport;
import org.example.aicvgenerator.service.model.CvImportStatus;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Runs one import on a worker thread and records its outcome on the job. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CvImportProcessor {

    static final String NOT_A_CV = "NOT_A_CV";
    static final String UNREADABLE_DOCUMENT = "UNREADABLE_DOCUMENT";
    static final String AI_SERVICE_ERROR = "AI_SERVICE_ERROR";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final CvService cvService;
    private final CvImportRepository cvImportRepository;
    private final CvImportEntityMapper mapper;

    /** Never throws: any failure ends up on the job so the worker thread survives. */
    public void run(UUID jobId, CvDocument document) {
        try {
            update(jobId, job -> job.setStatus(CvImportStatus.PROCESSING));
            CvSaveResult result = cvService.process(document);
            update(jobId, job -> {
                job.setStatus(CvImportStatus.COMPLETED);
                job.setCvId(result.cv().getId());
            });
        } catch (NotACvException e) {
            markFailed(jobId, NOT_A_CV, e.getMessage());
        } catch (UnreadableDocumentException | UnsupportedDocumentException e) {
            markFailed(jobId, UNREADABLE_DOCUMENT, e.getMessage());
        } catch (AiServiceException e) {
            log.warn("AI service failed for import {}", jobId, e);
            markFailed(jobId, AI_SERVICE_ERROR, "The AI service could not process the document");
        } catch (RuntimeException e) {
            log.error("Unexpected failure for import {}", jobId, e);
            markFailed(jobId, INTERNAL_ERROR, "The document could not be processed");
        }
    }

    /** Records a failure on the job; logs instead of throwing if even that is impossible. */
    private void markFailed(UUID jobId, String errorCode, String errorMessage) {
        try {
            update(jobId, job -> {
                job.setStatus(CvImportStatus.FAILED);
                job.setErrorCode(errorCode);
                job.setErrorMessage(errorMessage);
            });
        } catch (RuntimeException e) {
            log.error("Could not record failure {} for import {}", errorCode, jobId, e);
        }
    }

    private void update(UUID jobId, Consumer<CvImport> change) {
        CvImport job = cvImportRepository.findById(jobId)
                .map(mapper::toDomain)
                .orElseThrow(() -> new CvImportNotFoundException(jobId));
        change.accept(job);
        cvImportRepository.update(mapper.toEntity(job));
    }
}
