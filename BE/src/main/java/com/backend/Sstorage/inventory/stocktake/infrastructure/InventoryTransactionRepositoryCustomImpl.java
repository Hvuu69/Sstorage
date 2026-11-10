
package com.backend.Sstorage.inventory.stocktake.infrastructure;

import com.backend.Sstorage.inventory.stocktake.domain.InventoryTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class InventoryTransactionRepositoryCustomImpl
        implements InventoryTransactionRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<InventoryTransaction> search(
            UUID productId,
            String search,
            String source,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable
    ) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        String normalizedSearch =
                search == null || search.isBlank()
                        ? null
                        : search.trim().toLowerCase(java.util.Locale.ROOT);

        /*
         * DATA QUERY
         */
        CriteriaQuery<InventoryTransaction> query =
                cb.createQuery(InventoryTransaction.class);

        Root<InventoryTransaction> root =
                query.from(InventoryTransaction.class);

        /*
         * Fetch quan hệ cần hiển thị để tránh N+1 query.
         */
        Fetch<?, ?> productFetch =
                root.fetch("product", JoinType.INNER);

        productFetch.fetch("unit", JoinType.LEFT);

        Fetch<?, ?> stocktakeFetch =
                root.fetch("stocktakeItem", JoinType.LEFT);

        stocktakeFetch.fetch("stocktake", JoinType.LEFT);

        List<Predicate> predicates = buildPredicates(
                cb,
                root,
                productId,
                normalizedSearch,
                source,
                fromDate,
                toDate
        );

        query.select(root)
                .where(predicates.toArray(new Predicate[0]))
                .distinct(true);

        /*
         * Sắp xếp dữ liệu.
         */
        List<Order> orders = new ArrayList<>();

        if (pageable.getSort().isSorted()) {

            for (Sort.Order sortOrder : pageable.getSort()) {

                var path = root.get(sortOrder.getProperty());

                orders.add(
                        sortOrder.isAscending()
                                ? cb.asc(path)
                                : cb.desc(path)
                );
            }

        } else {
            orders.add(cb.desc(root.get("createdAt")));
        }

        /*
         * Thêm ID làm tiêu chí phụ để phân trang ổn định
         * khi nhiều giao dịch có cùng thời điểm.
         */
        boolean hasIdSort = pageable.getSort()
                .stream()
                .anyMatch(order -> order.getProperty().equals("id"));

        if (!hasIdSort) {
            orders.add(cb.desc(root.get("id")));
        }

        query.orderBy(orders);

        TypedQuery<InventoryTransaction> typedQuery =
                entityManager.createQuery(query);

        if (pageable.isPaged()) {
            typedQuery.setFirstResult(
                    Math.toIntExact(pageable.getOffset())
            );

            typedQuery.setMaxResults(pageable.getPageSize());
        }

        List<InventoryTransaction> content =
                typedQuery.getResultList();

        /*
         * COUNT QUERY
         *
         * Không fetch quan hệ trong câu COUNT.
         */
        CriteriaQuery<Long> countQuery =
                cb.createQuery(Long.class);

        Root<InventoryTransaction> countRoot =
                countQuery.from(InventoryTransaction.class);

        List<Predicate> countPredicates = buildPredicates(
                cb,
                countRoot,
                productId,
                normalizedSearch,
                source,
                fromDate,
                toDate
        );

        countQuery.select(cb.count(countRoot))
                .where(countPredicates.toArray(new Predicate[0]));

        Long total = entityManager
                .createQuery(countQuery)
                .getSingleResult();

        return new PageImpl<>(
                content,
                pageable,
                total
        );
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<InventoryTransaction> root,
            UUID productId,
            String search,
            String source,
            OffsetDateTime fromDate,
            OffsetDateTime toDate
    ) {

        List<Predicate> predicates = new ArrayList<>();

        /*
         * Lọc theo sản phẩm.
         */
        if (productId != null) {
            predicates.add(
                    cb.equal(
                            root.get("product").get("id"),
                            productId
                    )
            );
        }

        /*
         * Tìm kiếm SKU hoặc tên sản phẩm.
         *
         * Chỉ tạo LIKE khi search thực sự có giá trị.
         * Không truyền null vào PostgreSQL LIKE.
         */
        if (search != null && !search.isBlank()) {

            String escapedSearch = search
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");

            String pattern = "%" + escapedSearch + "%";

            Predicate skuPredicate = cb.like(
                    cb.lower(root.get("product").get("sku")),
                    pattern,
                    '\\'
            );

            Predicate namePredicate = cb.like(
                    cb.lower(root.get("product").get("name")),
                    pattern,
                    '\\'
            );

            predicates.add(
                    cb.or(skuPredicate, namePredicate)
            );
        }

        /*
         * Lọc theo nguồn giao dịch.
         */
        if (source != null && !source.isBlank()) {

            switch (source) {

                case "STOCKTAKE" ->
                        predicates.add(
                                cb.isNotNull(
                                        root.get("stocktakeItem")
                                )
                        );

                case "INBOUND" ->
                        predicates.add(
                                cb.isNotNull(
                                        root.get("inboundItemId")
                                )
                        );

                case "OUTBOUND" ->
                        predicates.add(
                                cb.isNotNull(
                                        root.get("outboundItemId")
                                )
                        );

                default ->
                        predicates.add(cb.disjunction());
            }
        }

        /*
         * Lọc thời gian bắt đầu.
         */
        if (fromDate != null) {
            predicates.add(
                    cb.greaterThanOrEqualTo(
                            root.get("createdAt"),
                            fromDate
                    )
            );
        }

        /*
         * Lọc thời gian kết thúc.
         */
        if (toDate != null) {
            predicates.add(
                    cb.lessThanOrEqualTo(
                            root.get("createdAt"),
                            toDate
                    )
            );
        }

        return predicates;
    }
}
