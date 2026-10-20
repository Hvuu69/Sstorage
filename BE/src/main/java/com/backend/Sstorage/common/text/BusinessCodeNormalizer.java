package com.backend.Sstorage.common.text;

import java.util.Locale;

public final class BusinessCodeNormalizer {
    private BusinessCodeNormalizer() {
    }

    public static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
