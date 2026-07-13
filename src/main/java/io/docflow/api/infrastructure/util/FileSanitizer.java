package io.docflow.api.infrastructure.util;

import io.docflow.api.infrastructure.exception.InvalidRequestException;
import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.regex.Pattern;

@UtilityClass
public class FileSanitizer {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-.]");
    private static final int MAX_FILENAME_LENGTH = 100;

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "unnamed_file_" + System.currentTimeMillis();
        }

        if (input.contains("..")) {
            throw new InvalidRequestException("Invalid file path sequence detected.");
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String result = normalized.replaceAll("\\p{M}", "");

        result = NON_LATIN.matcher(result).replaceAll("_");

        if (result.length() > MAX_FILENAME_LENGTH) {
            result = result.substring(result.length() - MAX_FILENAME_LENGTH);
        }

        return result;
    }
}
