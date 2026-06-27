package kz.medpartners.core.service;

import kz.medpartners.core.domain.PartnerEntity;
import kz.medpartners.core.domain.PriceDocumentEntity;
import kz.medpartners.core.model.ParsedDocument;
import kz.medpartners.core.model.PriceItem;
import kz.medpartners.core.parser.ParserFactory;
import kz.medpartners.core.parser.ParserUtils;
import kz.medpartners.core.parser.PriceParser;
import kz.medpartners.core.repository.PartnerRepository;
import kz.medpartners.core.repository.PriceDocumentRepository;
import kz.medpartners.core.service.dto.PriceUploadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service to manage unpacking and execution of price list parsing for uploaded archives.
 * Implements strict error isolation: if a single file in the zip is corrupt, it records a DB error log
 * and proceeds with processing other files without crashing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParsingService {

    private final ParserFactory parserFactory;
    private final PartnerRepository partnerRepository;
    private final PriceDocumentRepository priceDocumentRepository;
    private final DocumentProcessingService documentProcessingService;

    /**
     * Entry point to parse a MultipartFile ZIP upload.
     */
    public List<UUID> processZipArchive(MultipartFile file) {
        log.info("Processing uploaded ZIP archive: {}", file.getOriginalFilename());
        try (InputStream is = file.getInputStream()) {
            return processZipArchive(is);
        } catch (IOException e) {
            log.error("Failed to read ZIP archive: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read uploaded ZIP archive", e);
        }
    }

    /**
     * Unpacks and parses price files contained within a ZIP InputStream.
     */
    public List<UUID> processZipArchive(InputStream zipInputStream) {
        List<UUID> processedDocIds = new ArrayList<>();
        Path tempDir = null;
        try {
            // Create a secure temporary directory to extract files into
            tempDir = Files.createTempDirectory("med_prices_unpack_");
            log.info("Temporary directory created: {}", tempDir.toAbsolutePath());

            try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name = entry.getName();
                    File targetFile = new File(tempDir.toFile(), name);

                    // Secure check: Prevent Zip Slip vulnerability
                    if (!targetFile.getCanonicalPath().startsWith(tempDir.toFile().getCanonicalPath())) {
                        log.warn("Security Alert: Zip Slip attempt blocked for path '{}'. Skipping.", entry.getName());
                        continue;
                    }

                    // Create any necessary subdirectories
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        Files.createDirectories(parent.toPath());
                    }

                    // Write unpacked content to temp file
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                    zis.closeEntry();

                    // Parse the individual file
                    UUID docId = processFile(targetFile);
                    if (docId != null) {
                        processedDocIds.add(docId);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to extract files from ZIP archive: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract ZIP archive", e);
        } finally {
            if (tempDir != null) {
                cleanUpTempDir(tempDir.toFile());
            }
        }

        return processedDocIds;
    }

    /**
     * Processes an individual price list file. Resolves clinic names to Partner entities,
     * parses items, and registers them.
     */
    private UUID processFile(File file) {
        String fileName = file.getName();

        // Ignore system files (like macOS desktop files)
        if (fileName.startsWith(".") || fileName.startsWith("__MACOSX")) {
            log.debug("Skipping system file: {}", fileName);
            return null;
        }

        PriceParser parser;
        try {
            parser = parserFactory.getParser(file);
        } catch (IllegalArgumentException e) {
            log.warn("Skipping file '{}': {}", fileName, e.getMessage());
            return null;
        }

        String clinicName = ParserUtils.extractClinicName(fileName);
        PartnerEntity partner = findOrCreatePartner(clinicName);

        try {
            // Parse using the selected parser
            ParsedDocument parsedDoc = parser.parse(file);

            // Map the ParsedDocument record to existing PriceUploadRequest DTO
            PriceUploadRequest request = mapToUploadRequest(parsedDoc, partner.getPartner_id(), fileName);

            // Call existing processing service for price history, verification, and persistence
            UUID docId = documentProcessingService.processParsedDocument(request);
            log.info("Parsed file '{}' successfully. Doc ID: {}", fileName, docId);
            return docId;
        } catch (Throwable t) {
            log.error("Failed to parse price file '{}': {}", fileName, t.getMessage(), t);

            // Log parsing failure to database (status = ERROR) without throwing exception
            return saveErrorDocument(partner, file, t);
        }
    }

    /**
     * Finds partner by name (case-insensitive) or creates a new one if not present.
     */
    private PartnerEntity findOrCreatePartner(String name) {
        return partnerRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    log.info("Partner '{}' not found in database. Auto-creating a new partner.", name);
                    PartnerEntity newPartner = PartnerEntity.builder()
                            .partner_id(UUID.randomUUID())
                            .name(name)
                            .city("Алматы")
                            .address("Автоматический импорт")
                            .is_active(true)
                            .build();
                    return partnerRepository.save(newPartner);
                });
    }

    /**
     * Maps our ParsedDocument domain model to the PriceUploadRequest DTO.
     * С добавлением встроенной дедупликации строк по ТЗ 4.4
     */
    private PriceUploadRequest mapToUploadRequest(ParsedDocument doc, UUID partnerId, String fileName) {
        List<PriceUploadRequest.ParsedItemDto> itemDtos = new ArrayList<>();

        // Хранилище для отслеживания уже добавленных уникальных комбинаций
        java.util.Set<String> seenItems = new java.util.HashSet<>();

        for (PriceItem item : doc.items()) {
            // Безопасно вытаскиваем и очищаем значения от лишних пробелов
            String rawName = item.serviceNameRaw() != null ? item.serviceNameRaw().trim() : "";
            String rawCode = item.serviceCode() != null ? item.serviceCode().trim() : "";

            // Если название пустое — по ТЗ 4.4 пропускаем строку
            if (rawName.isEmpty() || rawName.equalsIgnoreCase("nan")) {
                continue;
            }

            // Уникальный ключ позиции (Нижний регистр, чтобы исключить дубли из-за РЕГИСТРА букв)
            String uniqueKey = rawCode.toLowerCase() + "_" + rawName.toLowerCase();

            // Если такой ключ уже встречался в файле — пропускаем (Дедупликация)
            if (!seenItems.add(uniqueKey)) {
                log.debug("Дубликат позиции пропущен в файле {}: Код: {}, Название: {}", fileName, rawCode, rawName);
                continue;
            }

            PriceUploadRequest.ParsedItemDto itemDto = PriceUploadRequest.ParsedItemDto.builder()
                    .serviceNameRaw(rawName)
                    .serviceCodeSource(rawCode.isEmpty() ? null : rawCode)
                    .priceResidentKzt(item.residentPrice())
                    .priceNonresidentKzt(item.nonResidentPrice() != null ? item.nonResidentPrice() : item.residentPrice())
                    .priceOriginal(item.residentPrice())
                    .currencyOriginal(item.currency() != null ? item.currency() : "KZT")
                    .build();
            itemDtos.add(itemDto);
        }

        String format = extractFormat(fileName);

        log.info("Фильтрация дубликатов в '{}': изначальных строк {}, уникальных сохраненно {}",
                fileName, doc.items().size(), itemDtos.size());

        return PriceUploadRequest.builder()
                .partnerId(partnerId)
                .fileName(fileName)
                .fileFormat(format)
                .effectiveDate(doc.priceDate())
                .items(itemDtos)
                .build();
    }

    private String extractFormat(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "pdf";
        } else if (lower.endsWith(".docx")) {
            return "docx";
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "xlsx";
        }
        return "unknown";
    }

    /**
     * Persists a document record with "error" status containing the execution trace.
     */
    private UUID saveErrorDocument(PartnerEntity partner, File file, Throwable t) {
        try {
            UUID docId = UUID.randomUUID();
            String format = extractFormat(file.getName());

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            String logContent = "Exception during parsing:\n" + t.getMessage() + "\n\n" + sw.toString();

            PriceDocumentEntity errorDoc = PriceDocumentEntity.builder()
                    .docId(docId)
                    .partner(partner)
                    .fileName(file.getName())
                    .fileFormat(format)
                    .effectiveDate(LocalDate.now())
                    .parseStatus("error")
                    .parseLog(logContent.substring(0, Math.min(logContent.length(), 4000))) // Limit log sizes
                    .parsedAt(LocalDateTime.now())
                    .build();

            priceDocumentRepository.save(errorDoc);
            log.info("Saved error log to PriceDocumentEntity for file '{}' (ID: {})", file.getName(), docId);
            return docId;
        } catch (Exception dbEx) {
            log.error("Failed to save error status to database for file '{}': {}", file.getName(), dbEx.getMessage(), dbEx);
            return null;
        }
    }

    /**
     * Recursively deletes temporary directories/files.
     */
    private void cleanUpTempDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                cleanUpTempDir(f);
            }
        }
        try {
            Files.delete(file.toPath());
        } catch (IOException e) {
            log.warn("Failed to delete temp item: {}. Error: {}", file.getAbsolutePath(), e.getMessage());
        }
    }
}
