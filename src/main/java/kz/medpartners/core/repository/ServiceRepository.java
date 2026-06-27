package kz.medpartners.core.repository;

import kz.medpartners.core.domain.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {

    // ИСПРАВЛЕНО: Явный запрос в обход автоматического парсинга camelCase
    @Query("SELECT s FROM ServiceEntity s WHERE s.category = :category AND s.is_active = true")
    List<ServiceEntity> findAllByCategoryAndIsActiveTrue(@Param("category") String category);
}