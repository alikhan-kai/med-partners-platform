package kz.medpartners.core.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Record representing a fully parsed price list document.
 *
 * @param clinicName Name of the clinic extracted from the document or its filename.
 * @param priceDate  Effective date of the price list.
 * @param items      List of parsed price items.
 */
public record ParsedDocument(
        String clinicName,
        LocalDate priceDate,
        List<PriceItem> items
) {}
