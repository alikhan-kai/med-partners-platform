package kz.medpartners.core.repository;

import kz.medpartners.core.domain.PriceItemEntity;
import org.springframework.data.jpa.repository.JpaRepository; // ИСПРАВЛЕНО: jpa вместо jupiter
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PriceItemRepository extends JpaRepository<PriceItemEntity, UUID> {

    // Получить все активные прайсы партнеров для конкретной эталонной услуги
    List<PriceItemEntity> findAllByServiceServiceIdAndIsActiveTrue(UUID serviceId);

    // Получить всю историю изменений цен по конкретной сырой услуге партнёра (версионирование)
    List<PriceItemEntity> findAllByPartnerPartnerIdAndServiceNameRawOrderByEffectiveDateDesc(UUID partnerId, String serviceNameRaw);

    // Получить список несопоставленных позиций для очереди ручной разметки оператором
    List<PriceItemEntity> findAllByServiceIsNull();
}