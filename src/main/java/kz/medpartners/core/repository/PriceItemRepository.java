package kz.medpartners.core.repository;

import kz.medpartners.core.domain.PriceItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PriceItemRepository extends JpaRepository<PriceItemEntity, UUID> {

    // 1. ИСПРАВЛЕНО: Явный HQL-запрос для обхода змеиного регистра в ServiceEntity
    @Query("SELECT p FROM PriceItemEntity p WHERE p.service.service_id = :serviceId AND p.is_active = true")
    List<PriceItemEntity> findAllByServiceServiceIdAndIsActiveTrue(@Param("serviceId") UUID serviceId);

    // 2. ИСПРАВЛЕНО: Явный HQL-запрос для обхода змеиного регистра в PartnerEntity
    @Query("SELECT p FROM PriceItemEntity p WHERE p.partner.partner_id = :partnerId AND p.service_name_raw = :serviceNameRaw ORDER BY p.effective_date DESC")
    List<PriceItemEntity> findAllByPartnerPartnerIdAndServiceNameRawOrderByEffectiveDateDesc(
            @Param("partnerId") UUID partnerId,
            @Param("serviceNameRaw") String serviceNameRaw
    );

    // 3. Этот метод работает отлично, так как просто проверяет поле на null
    List<PriceItemEntity> findAllByServiceIsNull();
}