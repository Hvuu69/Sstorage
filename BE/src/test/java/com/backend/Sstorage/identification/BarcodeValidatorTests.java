package com.backend.Sstorage.identification;

import com.backend.Sstorage.identification.application.BarcodeValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeValidatorTests {
    @Test
    void acceptsValidEan13AndUpcA() {
        assertTrue(BarcodeValidator.isValidEan13OrUpcA("4006381333931"));
        assertTrue(BarcodeValidator.isValidEan13OrUpcA("036000291452"));
    }

    @Test
    void rejectsWrongLengthCharactersAndCheckDigit() {
        assertFalse(BarcodeValidator.isValidEan13OrUpcA("4006381333932"));
        assertFalse(BarcodeValidator.isValidEan13OrUpcA("03600029145A"));
        assertFalse(BarcodeValidator.isValidEan13OrUpcA("123"));
    }
}
