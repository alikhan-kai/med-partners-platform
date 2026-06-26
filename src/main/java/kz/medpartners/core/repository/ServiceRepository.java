package kz.medpartners.core.repository;

import kz.medpartners.core.domain.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    // Автоматический SQL-запрос: SELECT * FROM services WHERE category = ? AND is_active = true
    List<ServiceEntity> findAllByCategoryAndIsActiveTrue(String category);
}