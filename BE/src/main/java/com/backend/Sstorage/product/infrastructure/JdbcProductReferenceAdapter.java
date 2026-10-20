package com.backend.Sstorage.product.infrastructure;

import com.backend.Sstorage.product.application.ProductReferencePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcProductReferenceAdapter implements ProductReferencePort {
    private static final String SQL = """
            SELECT CASE WHEN
                EXISTS (SELECT 1 FROM public.product_identifiers WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.inbound_items WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.stock_request_items WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.outbound_items WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.stocktake_items WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.inventory_transactions WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.demand_forecasts WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.inventory_policies WHERE product_id = :productId) OR
                EXISTS (SELECT 1 FROM public.inventory_alerts WHERE product_id = :productId)
            THEN TRUE ELSE FALSE END
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProductReferenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasBusinessReferences(UUID productId) {
        Boolean result = jdbcTemplate.queryForObject(SQL,
                new MapSqlParameterSource("productId", productId), Boolean.class);
        return Boolean.TRUE.equals(result);
    }
}
