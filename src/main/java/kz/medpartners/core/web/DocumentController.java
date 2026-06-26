package kz.medpartners.core.web;

import kz.medpartners.core.domain.PriceItemEntity;
import kz.medpartners.core.repository.PriceItemRepository;
import kz.medpartners.core.service.DocumentProcessingService;
import kz.medpartners.core.service.dto.PriceUploadRequest;
import kz.medpartners.core.service.dto.ServicePartnerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Для интеграции с фронтендом без проблем с CORS
public class DocumentController {

    private final DocumentProcessingService documentProcessingService;
    private final PriceItemRepository priceItemRepository;

    // 1. Endpoint для Python-парсера: принимает результаты обработки документа
    @PostMapping("/documents/upload-parsed")
    public ResponseEntity<UUID> uploadParsedDocument(@RequestBody PriceUploadRequest request) {
        UUID docId = documentProcessingService.processParsedDocument(request);
        return ResponseEntity.ok(docId);
    }

    // 2. GET /services/{id}/partners — Список партнёров, оказывающих услугу, с ценами (Требование ТЗ)
    @GetMapping("/services/{id}/partners")
    public ResponseEntity<List<ServicePartnerResponse>> getPartnersByService(@PathVariable("id") UUID serviceId) {
        List<PriceItemEntity> activeItems = priceItemRepository.findAllByServiceServiceIdAndIsActiveTrue(serviceId);

        List<ServicePartnerResponse> response = activeItems.stream()
                .map(item -> ServicePartnerResponse.builder()
                        .partnerId(item.getPartner().getPartner_id())
                        .partnerName(item.getPartner().getName())
                        .city(item.getPartner().getCity())
                        .address(item.getPartner().getAddress())
                        .priceResidentKzt(item.getPrice_resident_kzt())
                        .priceNonresidentKzt(item.getPrice_nonresident_kzt())
                        .effectiveDate(item.getEffective_date())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}