package kz.medpartners.core.parser;

import kz.medpartners.core.model.ParsedDocument;
import kz.medpartners.core.model.PriceItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Price list parser implementation for text-based PDF format using Apache PDFBox.
 * Extracts textual data, orders it geometrically, and processes it line-by-line using regex patterns.
 */
@Component
@Slf4j
public class PdfParser implements PriceParser {

    // Regex pattern matching optional service code, service name, and one or two price values at the end of the line
    private static final Pattern SERVICE_LINE_PATTERN = Pattern.compile(
            "^(?:([A-Z]{1,4}[0-9]+[A-Za-z0-9.\\-/]*|[0-9]{1,5})\\s+)?(.+?)\\s+(\\d[\\d\\s.,]*)(?:\\s+(\\d[\\d\\s.,]*))?$"
    );

    @Override
    public ParsedDocument parse(File file) throws Exception {
        log.info("Parsing text PDF file: {}", file.getName());
        
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Sort by layout position to reconstruct rows in table-like structures
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);

            String clinicName = ParserUtils.extractClinicName(file.getName());
            LocalDate priceDate = ParserUtils.extractDate(file.getName(), fullText);

            List<PriceItem> items = new ArrayList<>();
            String[] lines = fullText.split("\\r?\\n");

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                Matcher matcher = SERVICE_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String code = matcher.group(1);
                    String nameRaw = matcher.group(2).trim();
                    String resPriceText = matcher.group(3);
                    String nonResPriceText = matcher.group(4);

                    // Skip lines containing headers
                    String nameLower = nameRaw.toLowerCase();
                    if (nameLower.contains("наименование") || nameLower.contains("услуг") || 
                        nameLower.contains("стоимость") || nameLower.contains("цена") ||
                        nameLower.contains("код") || nameLower.contains("шифр")) {
                        continue;
                    }

                    BigDecimal residentPrice = ParserUtils.parsePrice(resPriceText);
                    BigDecimal nonResidentPrice = nonResPriceText != null ? ParserUtils.parsePrice(nonResPriceText) : null;

                    // Skip lines with zero prices and no code (usually section titles)
                    if (residentPrice.compareTo(BigDecimal.ZERO) == 0 && code == null) {
                        continue;
                    }

                    items.add(new PriceItem(nameRaw, residentPrice, nonResidentPrice, "KZT", code));
                }
            }

            log.info("Successfully parsed {} items from PDF file '{}'", items.size(), file.getName());
            return new ParsedDocument(clinicName, priceDate, items);
        }
    }
}
