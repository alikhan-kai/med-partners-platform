package kz.medpartners.core.repository;

import kz.medpartners.core.domain.PartnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartnerRepository extends JpaRepository<PartnerEntity, UUID> {

    // Явный JPQL-запрос, который на 100% проигнорирует имя метода и выполнит чистый SQL
    @Query("SELECT p FROM PartnerEntity p WHERE p.city = :city AND p.is_active = :isActive")
    List<PartnerEntity> findAllByCityAndIsActiveCustom(@Param("city") String city, @Param("isActive") Boolean isActive);

    java.util.Optional<PartnerEntity> findByNameIgnoreCase(String name);
}