//package kz.medpartners.core.parser;
//
//import kz.medpartners.core.model.ParsedDocument;
//import kz.medpartners.core.model.PriceItem;
//import lombok.extern.slf4j.Slf4j;
//import net.sourceforge.tess4j.Tesseract;
//import net.sourceforge.tess4j.TesseractException;
//import org.apache.pdfbox.Loader;
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.rendering.ImageType;
//import org.apache.pdfbox.rendering.PDFRenderer;
//import org.springframework.stereotype.Component;
//
//import java.awt.image.BufferedImage;
//import java.io.File;
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
///**
// * Price list parser implementation for scanned PDF formats using Tess4J (Tesseract OCR).
// * Renders document pages as high-resolution images, executes OCR, and applies line patterns
// * to extract medical service names, codes, and pricing.
// */
//@Component
//@Slf4j
//public class ScanPdfParser implements PriceParser {
//
//    private static final Pattern SERVICE_LINE_PATTERN = Pattern.compile(
//            "^(?:([A-Z]{1,4}[0-9]+[A-Za-z0-9.\\-/]*|[0-9]{1,5})\\s+)?(.+?)\\s+(\\d[\\d\\s.,]*)(?:\\s+(\\d[\\d\\s.,]*))?$"
//    );
//
//    @Override
//    public ParsedDocument parse(File file) throws Exception {
//        log.info("Parsing scanned PDF file using OCR: {}", file.getName());
//
//        String clinicName = ParserUtils.extractClinicName(file.getName());
//        StringBuilder fullText = new StringBuilder();
//
//        try {
//            Tesseract tesseract = new Tesseract();
//            // Load Russian and English dictionaries for bilingual clinics
//            tesseract.setLanguage("rus+eng");
//
//            // Set datapath if defined in environment variables
//            String tessdataPath = System.getenv("TESSDATA_PREFIX");
//            if (tessdataPath != null && !tessdataPath.isBlank()) {
//                tesseract.setDatapath(tessdataPath);
//            }
//
//            try (PDDocument doc = Loader.loadPDF(file)) {
//                PDFRenderer pdfRenderer = new PDFRenderer(doc);
//                int pageCount = doc.getNumberOfPages();
//                log.info("Rendering {} pages from scanned PDF '{}' for OCR...", pageCount, file.getName());
//
//                for (int page = 0; page < pageCount; page++) {
//                    log.info("Running OCR on page {}/{} for '{}'", page + 1, pageCount, file.getName());
//                    // Render page at 150 DPI, which is ideal for OCR without generating out-of-memory errors
//                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 150, ImageType.RGB);
//                    String pageText = tesseract.doOCR(image);
//                    fullText.append(pageText).append("\n");
//                }
//            }
//        } catch (LinkageError e) {
//            log.error("Failed to load native Tesseract OCR libraries for file '{}'. Error: {}", file.getName(), e.getMessage());
//            throw new RuntimeException("Tesseract OCR native libraries are not installed or configured on the host system: " + e.getMessage(), e);
//        } catch (TesseractException e) {
//            log.error("Tesseract execution failed during OCR on file '{}': {}", file.getName(), e.getMessage());
//            throw new RuntimeException("Tesseract OCR execution failed: " + e.getMessage(), e);
//        }
//
//        String extractedText = fullText.toString();
//        LocalDate priceDate = ParserUtils.extractDate(file.getName(), extractedText);
//        List<PriceItem> items = new ArrayList<>();
//        String[] lines = extractedText.split("\\r?\\n");
//
//        for (String line : lines) {
//            line = line.trim();
//            if (line.isEmpty()) {
//                continue;
//            }
//
//            Matcher matcher = SERVICE_LINE_PATTERN.matcher(line);
//            if (matcher.matches()) {
//                String code = matcher.group(1);
//                String nameRaw = matcher.group(2).trim();
//                String resPriceText = matcher.group(3);
//                String nonResPriceText = matcher.group(4);
//
//                String nameLower = nameRaw.toLowerCase();
//                if (nameLower.contains("наименование") || nameLower.contains("услуг") ||
//                    nameLower.contains("стоимость") || nameLower.contains("цена") ||
//                    nameLower.contains("код") || nameLower.contains("шифр")) {
//                    continue;
//                }
//
//                BigDecimal residentPrice = ParserUtils.parsePrice(resPriceText);
//                BigDecimal nonResidentPrice = nonResPriceText != null ? ParserUtils.parsePrice(nonResPriceText) : null;
//
//                if (residentPrice.compareTo(BigDecimal.ZERO) == 0 && code == null) {
//                    continue;
//                }
//
//                items.add(new PriceItem(nameRaw, residentPrice, nonResidentPrice, "KZT", code));
//            }
//        }
//
//        log.info("Successfully extracted {} items from scanned PDF file '{}'", items.size(), file.getName());
//        return new ParsedDocument(clinicName, priceDate, items);
//    }
//}



package kz.medpartners.core.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.medpartners.core.model.ParsedDocument;
import kz.medpartners.core.model.PriceItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ScanPdfParser implements PriceParser {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_API_KEY = "AQ.Ab8RN6KwbiqRxhQslhz9Bhr3wUUL5vqUJF_akwjuz44P1LaH_w";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY;

    @Override
    public ParsedDocument parse(File file) throws Exception {
        log.info("=== ИИ-ПОДХВАТ === Парсинг скана через Gemini 2.5 Flash: {}", file.getName());

        String clinicName = ParserUtils.extractClinicName(file.getName());
        LocalDate priceDate = ParserUtils.extractDate(file.getName(), null);
        List<PriceItem> items = new ArrayList<>();

        try {
            // 1. Кодируем скан-файл в Base64, чтобы передать его по сети в API Google
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64File = Base64.getEncoder().encodeToString(fileContent);

            // 2. Формируем JSON-запрос для Gemini (Промпт + Файл)
            String jsonRequest = buildGeminiJsonPayload(base64File, "application/pdf");

            // 3. Настраиваем заголовки HTTP
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonRequest, headers);

            // 4. Делаем запрос к ИИ
            ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_URL, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // 5. Разбираем текстовый ответ от Gemini
                String aiTextResult = parseTextFromGeminiResponse(response.getBody());
                log.info("ИИ успешно вернул текст скана для {}", file.getName());

                // Передаем текст в существующий регулярный парсер строк, который написал твой друг
                String[] lines = aiTextResult.split("\\r?\\n");
                for (String line : lines) {
                    processLine(line, items);
                }
            }

        } catch (Exception e) {
            log.error("Ошибка ИИ-парсинга для файла {}: {}", file.getName(), e.getMessage());
            // Если ИИ упал (например, кончился лимит), возвращаем пустой док, чтобы не вешать весь архив
        }

        return new ParsedDocument(clinicName, priceDate, items);
    }

    private void processLine(String line, List<PriceItem> items) {
        // Здесь вызывается та же логика с Matcher, которая уже есть в твоем ScanPdfParser
        // Она вытащит коды и цены из текста, который распознал Gemini
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "^(?:([A-Z]{1,4}[0-9]+[A-Za-z0-9.\\-/]*|[0-9]{1,5})\\s+)?(.+?)\\s+(\\d[\\d\\s.,]*)(?:\\s+(\\d[\\d\\s.,]*))?$"
        );
        java.util.regex.Matcher matcher = pattern.matcher(line.trim());
        if (matcher.matches()) {
            String code = matcher.group(1);
            String nameRaw = matcher.group(2).trim();
            BigDecimal residentPrice = ParserUtils.parsePrice(matcher.group(3));
            BigDecimal nonResidentPrice = matcher.group(4) != null ? ParserUtils.parsePrice(matcher.group(4)) : null;

            if (residentPrice.compareTo(BigDecimal.ZERO) > 0 && !nameRaw.toLowerCase().contains("цена")) {
                items.add(new PriceItem(nameRaw, residentPrice, nonResidentPrice, "KZT", code));
            }
        }
    }

    private String buildGeminiJsonPayload(String base64Data, String mimeType) {
        return "{\n" +
                "  \"contents\": [{\n" +
                "    \"parts\":[\n" +
                "      {\"text\": \"Ты — профессиональный OCR-модуль медицинских систем. Прочитай этот скан прайс-листа клиники и выведи все строки таблицы. Формат вывода строго: Код_услуги Название_услуги Цена. Не пиши никаких пояснений, только строки таблицы.\"},\n" +
                "      {\n" +
                "        \"inlineData\": {\n" +
                "          \"mimeType\":\"" + mimeType + "\",\n" +
                "          \"data\": \"" + base64Data + "\"\n" +
                "        }\n" +
                "      }\n" +
                "    ]\n" +
                "  }]\n" +
                "}";
    }

    private String parseTextFromGeminiResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();
    }
}