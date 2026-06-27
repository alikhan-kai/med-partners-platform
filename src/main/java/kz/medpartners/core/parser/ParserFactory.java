package kz.medpartners.core.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Factory class to determine and supply the correct {@link PriceParser} for a given file.
 * Auto-detects text vs scanned PDF using text density heuristics on the first page.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParserFactory {

    private final PdfParser pdfParser;
    private final ScanPdfParser scanPdfParser;
    private final DocxParser docxParser;
    private final ExcelParser excelParser;

    /**
     * Determines and returns the appropriate parser for the given file.
     *
     * @param file Price list file.
     * @return The matching {@link PriceParser} implementation.
     * @throws IllegalArgumentException If the file extension is unsupported.
     */
    public PriceParser getParser(File file) {
        String fileName = file.getName().toLowerCase();
        
        if (fileName.endsWith(".pdf")) {
            if (isScannedPdf(file)) {
                log.info("File '{}' detected as scanned PDF. Selecting ScanPdfParser.", file.getName());
                return scanPdfParser;
            } else {
                log.info("File '{}' detected as text PDF. Selecting PdfParser.", file.getName());
                return pdfParser;
            }
        } else if (fileName.endsWith(".docx")) {
            log.info("Selecting DocxParser for file '{}'.", file.getName());
            return docxParser;
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            log.info("Selecting ExcelParser for file '{}'.", file.getName());
            return excelParser;
        } else {
            throw new IllegalArgumentException("Unsupported file format: " + file.getName());
        }
    }

    /**
     * Heuristic to determine if a PDF file is scanned or textual.
     * Extracts text from the first page: if less than 50 characters of text are found,
     * it is classified as a scanned PDF.
     */
    private boolean isScannedPdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            if (document.getNumberOfPages() == 0) {
                return true;
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);
            return text == null || text.trim().length() < 50;
        } catch (IOException e) {
            log.warn("Error reading PDF structure of '{}', defaulting to ScanPdfParser. Details: {}", file.getName(), e.getMessage());
            return true;
        }
    }
}
