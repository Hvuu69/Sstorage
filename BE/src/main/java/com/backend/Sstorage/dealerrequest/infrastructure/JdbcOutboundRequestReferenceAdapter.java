package com.backend.Sstorage.dealerrequest.infrastructure;

import com.backend.Sstorage.dealerrequest.application.OutboundRequestReferencePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class JdbcOutboundRequestReferenceAdapter implements OutboundRequestReferencePort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboundRequestReferenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<UUID> activeOutboundRequestIds() {
        return new HashSet<>(jdbcTemplate.query(
                "select request_id from public.outbound_receipts where status <> 'CANCELLED'",
                (resultSet, rowNumber) -> resultSet.getObject("request_id", UUID.class)));
    }
}
