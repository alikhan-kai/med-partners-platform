package kz.medpartners.core.repository;

import kz.medpartners.core.domain.PriceDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PriceDocumentRepository extends JpaRepository<PriceDocumentEntity, UUID> {
    // Поиск документов по статусу (нужно для дашборда администратора)
    List<PriceDocumentEntity> findAllByParseStatus(String parseStatus);
}