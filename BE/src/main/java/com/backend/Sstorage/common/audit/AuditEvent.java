package com.backend.Sstorage.common.audit;

import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        String action,
        String entityType,
        UUID entityId,
        String actor,
        Map<String, Object> oldValues,
        Map<String, Object> newValues
) {
}
