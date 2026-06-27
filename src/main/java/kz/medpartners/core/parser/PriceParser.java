package kz.medpartners.core.parser;

import kz.medpartners.core.model.ParsedDocument;
import java.io.File;

/**
 * Unified interface for price list parsers.
 * Every file format parser must implement this interface.
 */
public interface PriceParser {

    /**
     * Parses the given price list file.
     *
     * @param file Price list file.
     * @return The parsed document structure.
     * @throws Exception If an error occurs during parsing.
     */
    ParsedDocument parse(File file) throws Exception;
}
