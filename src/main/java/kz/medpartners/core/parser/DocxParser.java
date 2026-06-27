package kz.medpartners.core.parser;

import kz.medpartners.core.model.ParsedDocument;
import kz.medpartners.core.model.PriceItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Price list parser implementation for DOCX format using Apache POI.
 * Scans tables for headers such as "код", "наименование", "стоимость", and "цена" to map services.
 */
@Component
@Slf4j
public class DocxParser implements PriceParser {

    @Override
    public ParsedDocument parse(File file) throws Exception {
        log.info("Parsing DOCX file: {}", file.getName());
        
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            // Extract plain text to search for a date or general details
            StringBuilder fullText = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                fullText.append(p.getText()).append("\n");
            }

            String clinicName = ParserUtils.extractClinicName(file.getName());
            LocalDate priceDate = ParserUtils.extractDate(file.getName(), fullText.toString());

            List<PriceItem> items = new ArrayList<>();

            // Parse tables
            for (XWPFTable table : doc.getTables()) {
                List<XWPFTableRow> rows = table.getRows();
                if (rows.isEmpty()) {
                    continue;
                }

                int codeCol = -1;
                int nameCol = -1;
                int priceCol = -1;
                int nonResPriceCol = -1;

                // Identify columns by looking at the first 2 rows of headers
                for (int r = 0; r < Math.min(2, rows.size()); r++) {
                    XWPFTableRow row = rows.get(r);
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int c = 0; c < cells.size(); c++) {
                        String cellText = cells.get(c).getText().toLowerCase().trim();
                        if (cellText.contains("код") || cellText.contains("шифр")) {
                            codeCol = c;
                        } else if (cellText.contains("наименование") || cellText.contains("услуг") || cellText.contains("название")) {
                            nameCol = c;
                        } else if (cellText.contains("стоимость") || cellText.contains("цена") || cellText.contains("тенге") || cellText.contains("resident") || cellText.contains("резидент")) {
                            if (cellText.contains("нерезидент") || cellText.contains("non")) {
                                nonResPriceCol = c;
                            } else {
                                priceCol = c;
                            }
                        }
                    }
                }

                // If headers are not matched, fall back to sensible defaults based on column count
                if (nameCol == -1 && priceCol == -1) {
                    int numCols = rows.get(0).getTableCells().size();
                    if (numCols >= 2) {
                        nameCol = numCols == 2 ? 0 : 1;
                        priceCol = numCols == 2 ? 1 : 2;
                        if (numCols >= 3) {
                            codeCol = 0;
                        }
                    } else {
                        continue; // Skip single column tables
                    }
                } else {
                    if (nameCol == -1) nameCol = 0;
                    if (priceCol == -1) priceCol = 1;
                }

                // Parse records
                for (int r = 1; r < rows.size(); r++) {
                    XWPFTableRow row = rows.get(r);
                    List<XWPFTableCell> cells = row.getTableCells();
                    if (cells.size() <= Math.max(nameCol, priceCol)) {
                        continue;
                    }

                    String nameRaw = cells.get(nameCol).getText().trim();
                    if (nameRaw.isEmpty() || nameRaw.toLowerCase().contains("наименование") || nameRaw.toLowerCase().contains("услуг")) {
                        continue;
                    }

                    String code = null;
                    if (codeCol != -1 && cells.size() > codeCol) {
                        code = cells.get(codeCol).getText().trim();
                        if (code.isEmpty()) {
                            code = null;
                        }
                    }

                    String priceText = cells.get(priceCol).getText();
                    BigDecimal residentPrice = ParserUtils.parsePrice(priceText);

                    BigDecimal nonResidentPrice = null;
                    if (nonResPriceCol != -1 && cells.size() > nonResPriceCol) {
                        nonResidentPrice = ParserUtils.parsePrice(cells.get(nonResPriceCol).getText());
                    }

                    // Skip headers and empty rows that have no price and no code
                    if (residentPrice.compareTo(BigDecimal.ZERO) == 0 && code == null) {
                        continue;
                    }

                    items.add(new PriceItem(nameRaw, residentPrice, nonResidentPrice, "KZT", code));
                }
            }

            log.info("Successfully parsed {} items from DOCX file '{}'", items.size(), file.getName());
            return new ParsedDocument(clinicName, priceDate, items);
        }
    }
}
