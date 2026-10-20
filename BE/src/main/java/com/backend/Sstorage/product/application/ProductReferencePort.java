package com.backend.Sstorage.product.application;

import java.util.UUID;

public interface ProductReferencePort {
    boolean hasBusinessReferences(UUID productId);
}
