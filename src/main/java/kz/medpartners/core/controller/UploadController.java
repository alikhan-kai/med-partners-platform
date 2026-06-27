package kz.medpartners.core.controller;

import kz.medpartners.core.service.ParsingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Controller to handle API file upload requests.
 * Exposes endpoint to accept a ZIP archive containing price list files.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class UploadController {

    private final ParsingService parsingService;

    /**
     * Endpoint to upload and parse a ZIP file containing multiple price lists.
     *
     * @param file ZIP archive file.
     * @return List of generated document IDs.
     */
    @PostMapping(value = "/documents/upload-zip", consumes = "multipart/form-data")
    public ResponseEntity<List<UUID>> uploadZipArchive(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Attempted upload of an empty file.");
            return ResponseEntity.badRequest().build();
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            log.warn("Uploaded file '{}' is not a ZIP archive.", filename);
            return ResponseEntity.badRequest().build();
        }

        log.info("Received ZIP archive '{}' ({} bytes) for processing.", filename, file.getSize());
        List<UUID> docIds = parsingService.processZipArchive(file);
        return ResponseEntity.ok(docIds);
    }
}
