package com.backend.Sstorage.identification.application;

public final class BarcodeValidator {
    private BarcodeValidator() {
    }

    public static boolean isValidEan13OrUpcA(String value) {
        if (value == null || !(value.matches("\\d{12}") || value.matches("\\d{13}"))) {
            return false;
        }
        int sum = 0;
        for (int index = value.length() - 2, position = 1; index >= 0; index--, position++) {
            int digit = value.charAt(index) - '0';
            sum += position % 2 == 1 ? digit * 3 : digit;
        }
        int expected = (10 - (sum % 10)) % 10;
        return expected == value.charAt(value.length() - 1) - '0';
    }
}
