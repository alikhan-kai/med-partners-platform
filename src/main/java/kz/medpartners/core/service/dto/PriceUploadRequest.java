package kz.medpartners.core.service.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceUploadRequest {
    private UUID partnerId;
    private String fileName;
    private String fileFormat;
    private LocalDate effectiveDate;
    private List<ParsedItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedItemDto {
        private String serviceNameRaw;
        private String serviceCodeSource;
        private UUID serviceId; // Будет заполнен, если Python-ИИ смог нормализовать автоматически
        private BigDecimal priceResidentKzt;
        private BigDecimal priceNonresidentKzt;
        private BigDecimal priceOriginal;
        private String currencyOriginal;
    }
}