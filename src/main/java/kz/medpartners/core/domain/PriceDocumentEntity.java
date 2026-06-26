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
    private UUID doc_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerEntity partner; // Связь с клиникой, которой принадлежит документ

    @Column(name = "file_name", nullable = false, columnDefinition = "TEXT")
    private String file_name;

    @Column(name = "file_format", length = 20)
    private String file_format; // pdf / docx / xlsx / scan_pdf

    @Column(name = "effective_date")
    private LocalDate effective_date;

    @Column(name = "parsed_at")
    private LocalDateTime parsed_at;

    @Column(name = "parse_status", length = 50)
    @Builder.Default
    private String parse_status = "pending"; // pending / processing / done / error / needs_review

    @Column(name = "parse_log", columnDefinition = "TEXT")
    private String parse_log;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String raw_content;

    @PrePersist
    protected void onPersist() {
        this.parsed_at = LocalDateTime.now();
    }
}