package kz.medpartners.core.parser;

import kz.medpartners.core.model.ParsedDocument;
import kz.medpartners.core.model.PriceItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Price list parser implementation for XLS/XLSX format using Apache POI.
 * Automatically identifies data schemas and parses columns dynamically.
 */
@Component
@Slf4j
public class ExcelParser implements PriceParser {

    @Override
    public ParsedDocument parse(File file) throws Exception {
        log.info("Parsing Excel file: {}", file.getName());
        
        String clinicName = ParserUtils.extractClinicName(file.getName());
        // For Excel, we default to filename extraction for date, since sheets are tabular data
        LocalDate priceDate = ParserUtils.extractDate(file.getName(), null);
        
        List<PriceItem> items = new ArrayList<>();
        
        try (Workbook workbook = WorkbookFactory.create(file)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    continue;
                }
                
                int codeCol = -1;
                int nameCol = -1;
                int priceCol = -1;
                int nonResPriceCol = -1;
                int headerRowIndex = -1;
                
                // Identify columns by searching the first 15 rows for headers
                int maxHeaderSearch = Math.min(15, sheet.getLastRowNum() + 1);
                for (int r = 0; r < maxHeaderSearch; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) {
                            continue;
                        }
                        
                        String cellVal = getCellText(cell).toLowerCase().trim();
                        if (cellVal.contains("код") || cellVal.contains("шифр")) {
                            codeCol = c;
                        } else if (cellVal.contains("наименование") || cellVal.contains("услуг") || cellVal.contains("название")) {
                            nameCol = c;
                        } else if (cellVal.contains("стоимость") || cellVal.contains("цена") || cellVal.contains("тенге") || cellVal.contains("resident") || cellVal.contains("резидент")) {
                            if (cellVal.contains("нерезидент") || cellVal.contains("non")) {
                                nonResPriceCol = c;
                            } else {
                                priceCol = c;
                            }
                        }
                    }
                    
                    if (nameCol != -1 && priceCol != -1) {
                        headerRowIndex = r;
                        break;
                    }
                }
                
                // If header is not found, fallback to guessing columns from the first row with >= 2 cells
                if (nameCol == -1 && priceCol == -1) {
                    for (int r = 0; r < maxHeaderSearch; r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) {
                            continue;
                        }
                        
                        int cellCount = 0;
                        for (int c = 0; c < row.getLastCellNum(); c++) {
                            if (row.getCell(c) != null && !getCellText(row.getCell(c)).trim().isEmpty()) {
                                cellCount++;
                            }
                        }
                        
                        if (cellCount >= 2) {
                            headerRowIndex = r;
                            nameCol = 0;
                            priceCol = 1;
                            if (row.getLastCellNum() >= 3) {
                                nameCol = 1;
                                priceCol = 2;
                                codeCol = 0;
                            }
                            break;
                        }
                    }
                }
                
                if (headerRowIndex == -1) {
                    continue; // Skip empty or unrecognized sheet
                }
                
                // Read items under header row
                for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    
                    Cell nameCell = row.getCell(nameCol);
                    if (nameCell == null) {
                        continue;
                    }
                    
                    String nameRaw = getCellText(nameCell).trim();
                    if (nameRaw.isEmpty() || nameRaw.toLowerCase().contains("наименование") || nameRaw.toLowerCase().contains("услуг") || nameRaw.toLowerCase().contains("итого")) {
                        continue;
                    }
                    
                    String code = null;
                    if (codeCol != -1) {
                        Cell codeCell = row.getCell(codeCol);
                        if (codeCell != null) {
                            code = getCellText(codeCell).trim();
                            if (code.isEmpty()) {
                                code = null;
                            }
                        }
                    }
                    
                    BigDecimal residentPrice = BigDecimal.ZERO;
                    if (priceCol != -1) {
                        Cell priceCell = row.getCell(priceCol);
                        if (priceCell != null) {
                            residentPrice = getCellNumericValue(priceCell);
                        }
                    }
                    
                    BigDecimal nonResidentPrice = null;
                    if (nonResPriceCol != -1) {
                        Cell nonResPriceCell = row.getCell(nonResPriceCol);
                        if (nonResPriceCell != null) {
                            nonResidentPrice = getCellNumericValue(nonResPriceCell);
                        }
                    }
                    
                    // Skip rows that represent layout sections (0 price, no code)
                    if (residentPrice.compareTo(BigDecimal.ZERO) == 0 && code == null) {
                        continue;
                    }
                    
                    items.add(new PriceItem(nameRaw, residentPrice, nonResidentPrice, "KZT", code));
                }
            }
        }
        
        log.info("Successfully parsed {} items from Excel file '{}'", items.size(), file.getName());
        return new ParsedDocument(clinicName, priceDate, items);
    }
    
    /**
     * Helper to read cells as text safely.
     */
    private String getCellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        
        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == (long) d) {
                    return String.format("%d", (long) d);
                } else {
                    return String.valueOf(d);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
    
    /**
     * Helper to read cells as numeric BigDecimal safely.
     */
    private BigDecimal getCellNumericValue(Cell cell) {
        if (cell == null) {
            return BigDecimal.ZERO;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        } else if (type == CellType.STRING) {
            return ParserUtils.parsePrice(cell.getStringCellValue());
        }
        return BigDecimal.ZERO;
    }
}
