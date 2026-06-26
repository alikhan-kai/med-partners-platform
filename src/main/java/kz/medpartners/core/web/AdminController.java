package kz.medpartners.core.web;

import kz.medpartners.core.domain.PriceItemEntity;
import kz.medpartners.core.domain.ServiceEntity;
import kz.medpartners.core.repository.PriceItemRepository;
import kz.medpartners.core.repository.ServiceRepository;
import kz.medpartners.core.service.dto.UnmatchedItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final PriceItemRepository priceItemRepository;
    private final ServiceRepository serviceRepository;

    // 1. GET /unmatched — Список несопоставленных позиций (Требование ТЗ)
    @GetMapping("/unmatched")
    public ResponseEntity<List<UnmatchedItemResponse>> getUnmatchedItems() {
        List<PriceItemEntity> unmatched = priceItemRepository.findAllByServiceIsNull();

        List<UnmatchedItemResponse> response = unmatched.stream()
                .map(item -> UnmatchedItemResponse.builder()
                        .itemId(item.getItem_id())
                        .docId(item.getDocument().getDoc_id())
                        .fileName(item.getDocument().getFile_name())
                        .serviceNameRaw(item.getService_name_raw())
                        .priceResidentKzt(item.getPrice_resident_kzt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // 2. POST /match — Ручное сопоставление позиции прайса с услугой справочника (Требование ТЗ)
    @PostMapping("/match")
    public ResponseEntity<Void> matchServiceManual(
            @RequestParam("itemId") UUID itemId,
            @RequestParam("serviceId") UUID serviceId) {

        PriceItemEntity item = priceItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Price item not found"));

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service catalog item not found"));

        // Привязываем эталонную услугу и верифицируем запись
        item.setService(service);
        item.setIs_verified(true);
        item.setIs_active(true); // Теперь позиция становится валидной для выдачи клиентам

        priceItemRepository.save(item);
        return ResponseEntity.ok().build();
    }
}