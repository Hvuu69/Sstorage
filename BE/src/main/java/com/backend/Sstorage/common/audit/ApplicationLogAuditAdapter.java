package com.backend.Sstorage.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLogAuditAdapter implements AuditPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationLogAuditAdapter.class);

    @Override
    public void record(AuditEvent event) {
        LOGGER.info("AUDIT action={} entityType={} entityId={} actor={} oldValues={} newValues={}",
                event.action(), event.entityType(), event.entityId(), event.actor(),
                event.oldValues(), event.newValues());
    }
}
