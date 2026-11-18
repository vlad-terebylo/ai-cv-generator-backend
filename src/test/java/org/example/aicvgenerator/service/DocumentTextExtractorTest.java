package org.example.aicvgenerator.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.service.model.CvDocument;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void acceptsPdfAndDocxIgnoringCase() {
        assertThatCode(() -> extractor.requireSupported(new CvDocument("cv.PDF", new byte[] {1}))).doesNotThrowAnyException();
        assertThatCode(() -> extractor.requireSupported(new CvDocument("cv.docx", new byte[] {1}))).doesNotThrowAnyException();
    }

    @Test
    void rejectsOtherExtensionsAndMissingNames() {
        assertThatThrownBy(() -> extractor.requireSupported(new CvDocument("cv.txt", new byte[] {1})))
                .isInstanceOf(UnsupportedDocumentException.class);
        assertThatThrownBy(() -> extractor.requireSupported(new CvDocument(null, new byte[] {1})))
                .isInstanceOf(UnsupportedDocumentException.class);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> extractor.requireSupported(new CvDocument("cv.pdf", new byte[0])))
                .isInstanceOf(UnreadableDocumentException.class);
        assertThatThrownBy(() -> extractor.requireSupported(new CvDocument("cv.pdf", null)))
                .isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void extractRejectsAPdfThatIsNotReallyAPdf() {
        assertThatThrownBy(() -> extractor.extract(new CvDocument("cv.pdf", new byte[] {1, 2, 3})))
                .isInstanceOf(UnreadableDocumentException.class);
    }
}
