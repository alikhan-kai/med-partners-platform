package kz.medpartners.core.repository;

import kz.medpartners.core.domain.PartnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartnerRepository extends JpaRepository<PartnerEntity, UUID> {
    // Фильтрация партнёров по городу и статусу (требование API из ТЗ)
    List<PartnerEntity> findAllByCityAndIsActive(String city, Boolean isActive);
}