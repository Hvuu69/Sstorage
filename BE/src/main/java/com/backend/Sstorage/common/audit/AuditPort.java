package com.backend.Sstorage.common.audit;

public interface AuditPort {
    void record(AuditEvent event);
}
