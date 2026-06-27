package kz.medpartners.core.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for price list parsing.
 * Shared across different parser implementations.
 */
public final class ParserUtils {

    private ParserUtils() {
        // Utility class
    }

    /**
     * Extracts the clinic name from a filename by stripping the file extension
     * and removing common filler words (like "прайс", "год", numbers, dates).
     */
    public static String extractClinicName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Unknown Clinic";
        }
        
        String nameWithoutExt = filename;
        int extIndex = filename.lastIndexOf('.');
        if (extIndex > 0) {
            nameWithoutExt = filename.substring(0, extIndex);
        }
        
        // Remove common keywords and digits (years)
        String normalized = nameWithoutExt
                .replaceAll("(?i)\\bпрайс\\b", "")
                .replaceAll("(?i)\\bпрайс\\b", "")
                .replaceAll("(?i)\\bгод\\b", "")
                .replaceAll("\\b\\d{4}\\b", "") // Remove 4-digit years
                .replaceAll("[_\\-\\s]+", " ")  // Normalize separators to spaces
                .trim();
        
        return normalized.isEmpty() ? "Unknown Clinic" : normalized;
    }

    /**
     * Attempts to extract a date from the text content (dd.MM.yyyy format),
     * falling back to checking for a 4-digit year in the filename.
     */
    public static LocalDate extractDate(String filename, String content) {
        // 1. Try to find a date like dd.MM.yyyy in the document content
        if (content != null && !content.isBlank()) {
            Pattern datePattern = Pattern.compile("\\b(\\d{2})\\.(\\d{2})\\.(\\d{4})\\b");
            Matcher matcher = datePattern.matcher(content);
            if (matcher.find()) {
                try {
                    int day = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int year = Integer.parseInt(matcher.group(3));
                    return LocalDate.of(year, month, day);
                } catch (Exception e) {
                    // Ignore and try fallback
                }
            }
        }

        // 2. Look for a 4-digit year (like 2024, 2025, 2026) in the filename
        if (filename != null) {
            Pattern yearPattern = Pattern.compile("\\b(20\\d{2})\\b");
            Matcher matcher = yearPattern.matcher(filename);
            if (matcher.find()) {
                try {
                    int year = Integer.parseInt(matcher.group(1));
                    return LocalDate.of(year, 1, 1);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        // 3. Fallback to current date
        return LocalDate.now();
    }

    /**
     * Parses numeric price strings with potential thousand separators (spaces, commas, dots)
     * and decimal markers into a BigDecimal.
     */
    public static BigDecimal parsePrice(String text) {
        if (text == null) {
            return BigDecimal.ZERO;
        }
        
        // Remove currency symbols, units, and non-numeric letters (keep digits, dot, comma, minus)
        String clean = text.replaceAll("(?i)[^\\d.,\\-]", "").trim();
        if (clean.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Check if there is a fractional part with two decimal places (e.g. ,00 or .00) at the end
        if (clean.matches(".*,\\d{2}$")) {
            String decimalPart = clean.substring(clean.length() - 2);
            String integerPart = clean.substring(0, clean.length() - 3).replaceAll("[^\\d\\-]", "");
            clean = integerPart + "." + decimalPart;
        } else if (clean.matches(".*\\.\\d{2}$")) {
            String decimalPart = clean.substring(clean.length() - 2);
            String integerPart = clean.substring(0, clean.length() - 3).replaceAll("[^\\d\\-]", "");
            clean = integerPart + "." + decimalPart;
        } else {
            // Strip all formatting except digits (and minus sign if negative)
            clean = clean.replaceAll("[^\\d\\-]", "");
        }

        if (clean.isEmpty() || clean.equals("-")) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
