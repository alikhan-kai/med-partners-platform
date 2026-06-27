package kz.medpartners.core.service;

import kz.medpartners.core.domain.PriceItemEntity;
import kz.medpartners.core.domain.ServiceEntity;
import kz.medpartners.core.repository.PriceItemRepository;
import kz.medpartners.core.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MappingService {

    private final PriceItemRepository priceItemRepository;
    private final ServiceRepository serviceRepository; // Spring сам создаст инжект, так как он есть в папке repository

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private static final double SIMILARITY_THRESHOLD = 0.75; // Порог совпадения 75%

    @Transactional
    public void matchUnmappedItems() {
        log.info("=== СТАРТ МЭТЧИНГА === Сопоставление сырых цен с эталонным справочником по ТЗ 3.4...");

        // 1. Берем только те строки клиник, у которых поле service еще равно null
        List<PriceItemEntity> unmappedItems = priceItemRepository.findAllByServiceIsNull();

        // 2. Берем весь наш залитый справочник услуг
        List<ServiceEntity> referenceServices = serviceRepository.findAll();

        if (unmappedItems.isEmpty() || referenceServices.isEmpty()) {
            log.info("Нет данных для мэтчинга. Сырых строк без связи: {}, Услуг в справочнике: {}",
                    unmappedItems.size(), referenceServices.size());
            return;
        }

        int successCount = 0;

        for (PriceItemEntity item : unmappedItems) {
            // ИСПРАВЛЕНО: используем геттеры со змеиным регистром _
            if (item.getService_name_raw() == null) {
                continue;
            }

            String rawName = item.getService_name_raw().toLowerCase().trim();
            String rawCode = item.getService_code_source();

            ServiceEntity bestMatch = null;
            double maxSimilarity = 0.0;

            for (ServiceEntity ref : referenceServices) {
                if (ref.getService_name() == null) {
                    continue;
                }

                // Шаг А: Проверка по коду тарификатора
                if (rawCode != null && !rawCode.isEmpty() && rawCode.equalsIgnoreCase(ref.getIcd_code())) {
                    bestMatch = ref;
                    maxSimilarity = 1.0;
                    break;
                }

                // Шаг Б: Нечеткое сравнение
                String refName = ref.getService_name().toLowerCase().trim();
                double currentSimilarity = jaroWinkler.apply(rawName, refName);

                if (currentSimilarity > maxSimilarity) {
                    maxSimilarity = currentSimilarity;
                    bestMatch = ref;
                }
            }

            // 3. Если совпадение хорошее — связываем
            if (bestMatch != null && maxSimilarity >= SIMILARITY_THRESHOLD) {
                item.setService(bestMatch);
                successCount++;
                log.info("Успешный мэтчинг: '{}' -> '{}' (Уверенность: {}%)",
                        item.getService_name_raw(), bestMatch.getService_name(), (int)(maxSimilarity * 100));
            } else {
                log.warn("Позиция отправлена на ручную проверку: '{}'", item.getService_name_raw());
            }
        }

        // 4. Сохраняем измененные строки с проставленными связями в базу
        priceItemRepository.saveAll(unmappedItems);
        log.info("=== МЭТЧИНГ ЗАВЕРШЕН === Автоматически связано услуг: {} из {}", successCount, unmappedItems.size());
    }
}