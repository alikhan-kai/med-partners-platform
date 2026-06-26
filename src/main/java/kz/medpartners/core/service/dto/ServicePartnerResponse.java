package kz.medpartners.core.service.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePartnerResponse {
    private UUID partnerId;
    private String partnerName;
    private String city;
    private String address;
    private BigDecimal priceResidentKzt;
    private BigDecimal priceNonresidentKzt;
    private LocalDate effectiveDate;
}