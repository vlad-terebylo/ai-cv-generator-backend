package org.example.aicvgenerator.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final String maxFileSize;

    public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size}") String maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler({UnsupportedDocumentException.class, UnreadableDocumentException.class})
    public ProblemDetail handleBadDocument(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "The file exceeds the " + maxFileSize + " limit");
    }

    @ExceptionHandler({CvNotFoundException.class, CvImportNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ServerBusyException.class)
    public ProblemDetail handleServerBusy(ServerBusyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
