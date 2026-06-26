package kz.medpartners.core.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceItemEntity {

    @Id
    @Column(name = "item_id")
    private UUID item_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id", nullable = false)
    private PriceDocumentEntity document; // Ссылка на исходный документ

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private PartnerEntity partner; // Денормализация для быстрого поиска без JOIN-ов

    @Column(name = "service_name_raw", nullable = false, columnDefinition = "TEXT")
    private String service_name_raw;

    @Column(name = "service_code_source", length = 100)
    private String service_code_source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceEntity service; // Ссылка на эталонную услугу из справочника (nullable!)

    @Column(name = "price_resident_kzt", nullable = false, precision = 12, scale = 2)
    private BigDecimal price_resident_kzt;

    @Column(name = "price_nonresident_kzt", precision = 12, scale = 2)
    private BigDecimal price_nonresident_kzt;

    @Column(name = "price_original", precision = 12, scale = 2)
    private BigDecimal price_original;

    @Column(name = "currency_original", length = 10)
    @Builder.Default
    private String currency_original = "KZT";

    @Column(name = "is_verified")
    @Builder.Default
    private Boolean is_verified = false;

    @Column(name = "verification_note", columnDefinition = "TEXT")
    private String verification_note;

    @Column(name = "effective_date")
    private LocalDate effective_date;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean is_active = true;
}