package org.example.aicvgenerator.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.example.aicvgenerator.exception.UnreadableDocumentException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.service.model.CvDocument;
import org.springframework.stereotype.Service;

@Service
public class DocumentTextExtractor {

    /** Cheap checks that need no parsing: the file is not empty and is a .pdf or .docx. */
    public void requireSupported(CvDocument document) {
        if (document.content() == null || document.content().length == 0) {
            throw new UnreadableDocumentException("The uploaded file is empty");
        }
        String name = fileNameOf(document);
        if (!name.endsWith(".pdf") && !name.endsWith(".docx")) {
            throw new UnsupportedDocumentException("Only .pdf and .docx files are supported");
        }
    }

    public String extract(CvDocument document) {
        requireSupported(document);
        String text = fileNameOf(document).endsWith(".pdf")
                ? fromPdf(document.content())
                : fromDocx(document.content());
        if (text.isBlank()) {
            throw new UnreadableDocumentException("No text could be extracted from the file");
        }
        return text.strip();
    }

    private String fileNameOf(CvDocument document) {
        return document.fileName() == null ? "" : document.fileName().toLowerCase(Locale.ROOT);
    }

    private String fromPdf(byte[] content) {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(pdf);
        } catch (IOException e) {
            throw new UnreadableDocumentException("The PDF file could not be read", e);
        }
    }

    private String fromDocx(byte[] content) {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
            return extractor.getText();
        } catch (IOException | RuntimeException e) {
            throw new UnreadableDocumentException("The DOCX file could not be read", e);
        }
    }
}
