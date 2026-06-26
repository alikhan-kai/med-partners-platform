package kz.medpartners.core.service;

import kz.medpartners.core.domain.PartnerEntity;
import kz.medpartners.core.domain.PriceDocumentEntity;
import kz.medpartners.core.domain.PriceItemEntity;
import kz.medpartners.core.domain.ServiceEntity;
import kz.medpartners.core.repository.PartnerRepository;
import kz.medpartners.core.repository.PriceDocumentRepository;
import kz.medpartners.core.repository.PriceItemRepository;
import kz.medpartners.core.repository.ServiceRepository;
import kz.medpartners.core.service.dto.PriceUploadRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final PartnerRepository partnerRepository;
    private final PriceDocumentRepository priceDocumentRepository;
    private final PriceItemRepository priceItemRepository;
    private final ServiceRepository serviceRepository;

    @Transactional
    public UUID processParsedDocument(PriceUploadRequest request) {
        // 1. Проверяем существование партнёра
        PartnerEntity partner = partnerRepository.findById(request.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Partner not found with ID: " + request.getPartnerId()));

        // 2. Создаем запись о документе
        UUID docId = UUID.randomUUID();
        PriceDocumentEntity document = PriceDocumentEntity.builder()
                .doc_id(docId)
                .partner(partner)
                .file_name(request.getFileName())
                .file_format(request.getFileFormat())
                .effective_date(request.getEffectiveDate())
                .parse_status("processing")
                .build();

        priceDocumentRepository.save(document);

        boolean hasAnomaly = false;
        StringBuilder logBuilder = new StringBuilder();

        // 3. Обрабатываем каждую позицию прайса
        for (PriceUploadRequest.ParsedItemDto itemDto : request.getItems()) {

            // Проверяем базовое требование ТЗ: Название услуги не должно быть пустым
            if (itemDto.getServiceNameRaw() == null || itemDto.getServiceNameRaw().isBlank()) {
                logBuilder.append("Пропущена строка: пустое наименование услуги.\n");
                continue;
            }

            // Ищем предыдущую активную цену этой клиники по данному raw-наименованию для проверки аномалий
            List<PriceItemEntity> history = priceItemRepository
                    .findAllByPartnerPartnerIdAndServiceNameRawOrderByEffectiveDateDesc(partner.getPartner_id(), itemDto.getServiceNameRaw());

            String statusNote = null;
            boolean isAnomalyForThisItem = false;

            if (!history.isEmpty()) {
                PriceItemEntity lastActivePrice = history.get(0);
                BigDecimal oldPrice = lastActivePrice.getPrice_resident_kzt();
                BigDecimal newPrice = itemDto.getPriceResidentKzt();

                if (oldPrice != null && oldPrice.compareTo(BigDecimal.ZERO) > 0) {
                    // Вычисляем процент изменения: |(new - old) / old|
                    BigDecimal changePercent = newPrice.subtract(oldPrice).abs()
                            .divide(oldPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    // Если цена отличается от предыдущей версии > 50% — вешаем флаг аномалии
                    if (changePercent.compareTo(BigDecimal.valueOf(50)) > 0) {
                        hasAnomaly = true;
                        isAnomalyForThisItem = true;
                        statusNote = "Аномалия цены: изменение на " + changePercent.setScale(2, RoundingMode.HALF_UP) + "%";
                        logBuilder.append("Подозрение на аномалию в услуге '").append(itemDto.getServiceNameRaw()).append("'\n");
                    }
                }
            }

            // Дедупликация и версионирование: деактивируем старые записи, если аномалии нет
            if (!isAnomalyForThisItem && !history.isEmpty()) {
                for (PriceItemEntity oldItem : history) {
                    oldItem.setIs_active(false);
                }
                priceItemRepository.saveAll(history);
            }

            // Находим эталонную услугу, если ИИ передал serviceId
            ServiceEntity serviceCatalogEntity = null;
            if (itemDto.getServiceId() != null) {
                serviceCatalogEntity = serviceRepository.findById(itemDto.getServiceId()).orElse(null);
            }

            // Сохраняем позицию прайса
            PriceItemEntity priceItem = PriceItemEntity.builder()
                    .item_id(UUID.randomUUID())
                    .document(document)
                    .partner(partner)
                    .service_name_raw(itemDto.getServiceNameRaw())
                    .service_code_source(itemDto.getServiceCodeSource())
                    .service(serviceCatalogEntity) // Может быть null, тогда уйдёт в unmatched queue
                    .price_resident_kzt(itemDto.getPriceResidentKzt())
                    .price_nonresident_kzt(itemDto.getPriceNonresidentKzt())
                    .price_original(itemDto.getPriceOriginal())
                    .currency_original(itemDto.getCurrencyOriginal())
                    .verification_note(statusNote)
                    .effective_date(request.getEffectiveDate())
                    .is_active(!isAnomalyForThisItem) // Если аномалия, пока не делаем активной до проверки оператором
                    .build();

            priceItemRepository.save(priceItem);
        }

        // 4. Обновляем финальный статус документа
        if (hasAnomaly) {
            document.setParse_status("needs_review");
        } else {
            document.setParse_status("done");
        }
        document.setParse_log(logBuilder.toString());
        priceDocumentRepository.save(document);

        return docId;
    }
}