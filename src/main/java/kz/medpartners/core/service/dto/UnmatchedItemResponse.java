package kz.medpartners.core.service.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnmatchedItemResponse {
    private UUID itemId;
    private UUID docId;
    private String fileName;
    private String serviceNameRaw;
    private BigDecimal priceResidentKzt;
}