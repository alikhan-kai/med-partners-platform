package kz.medpartners.core.model;

import java.math.BigDecimal;

/**
 * Record representing a parsed price list item.
 *
 * @param serviceNameRaw     Raw name of the service from the document.
 * @param residentPrice      Price for local residents.
 * @param nonResidentPrice   Price for non-residents (can be null).
 * @param currency           Currency of the price (usually KZT).
 * @param serviceCode        Service code if available in the document (can be null).
 */
public record PriceItem(
        String serviceNameRaw,
        BigDecimal residentPrice,
        BigDecimal nonResidentPrice,
        String currency,
        String serviceCode
) {}
