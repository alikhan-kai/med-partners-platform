package kz.medpartners.core.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "price_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceDocumentEntity {

    @Id
    @Column(name = "doc_id")
    private UUID docId; // Исправлено: doc_id -> docId

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerEntity partner;

    @Column(name = "file_name", nullable = false, columnDefinition = "TEXT")
    private String fileName; // Исправлено: file_name -> fileName

    @Column(name = "file_format", length = 20)
    private String fileFormat; // Исправлено: file_format -> fileFormat

    @Column(name = "effective_date")
    private LocalDate effectiveDate; // Исправлено: effective_date -> effectiveDate

    @Column(name = "parsed_at")
    private LocalDateTime parsedAt; // Исправлено: parsed_at -> parsedAt

    @Column(name = "parse_status", length = 50)
    @Builder.Default
    private String parseStatus = "pending"; // ИСПРАВЛЕНО: теперь Spring Data найдет parseStatus!

    @Column(name = "parse_log", columnDefinition = "TEXT")
    private String parseLog; // Исправлено: parse_log -> parseLog

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent; // Исправлено: raw_content -> rawContent

    @PrePersist
    protected void onPersist() {
        this.parsedAt = LocalDateTime.now();
    }
}