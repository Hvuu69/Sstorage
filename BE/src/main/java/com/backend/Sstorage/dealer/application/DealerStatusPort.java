package com.backend.Sstorage.dealer.application;

import java.util.UUID;

/** Read-only integration contract for workflows that must validate a dealer. */
public interface DealerStatusPort {
    boolean isActive(UUID dealerId);
}
