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

    public String extract(CvDocument document) {
        String name = document.fileName() == null ? "" : document.fileName().toLowerCase(Locale.ROOT);
        if (document.content() == null || document.content().length == 0) {
            throw new UnreadableDocumentException("The uploaded file is empty");
        }
        String text;
        if (name.endsWith(".pdf")) {
            text = fromPdf(document.content());
        } else if (name.endsWith(".docx")) {
            text = fromDocx(document.content());
        } else {
            throw new UnsupportedDocumentException("Only .pdf and .docx files are supported");
        }
        if (text.isBlank()) {
            throw new UnreadableDocumentException("No text could be extracted from the file");
        }
        return text.strip();
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
