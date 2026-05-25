package com.karmayogi.form.repository;

import com.karmayogi.form.repository.FormRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Facet aggregation using UNION ALL native SQL.
 * Disjunctive facets: each facet excludes its own filter.
 * Column names from whitelist — no SQL injection risk.
 */
/**
 * @author anil
 */
@Repository
@RequiredArgsConstructor
public class FormRepositoryImpl implements FormRepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(FormRepositoryImpl.class);

    private final JdbcTemplate jdbc;

    // Whitelist — facet name to DB column name
    // Column names never come from user input — safe from SQL injection
    private static final Map<String, String> FACET_COL_MAP = Map.of(
            "status",      "status",
            "contextType", "contexttype",
            "orgId",       "orgid",
            "createdBy",   "createdby"
    );

    // SQL template — %s for column name (whitelist only), ? for values (bind params)
    private static final String FACET_SQL =
            "SELECT '%s' AS facet, %s AS value, COUNT(*) AS count " +
            "FROM forms WHERE 1=1 %s GROUP BY %s";

    @Override
    public Map<String, Object> getFacets(Map<String, Object> filters,
                                          List<String> facets) {

        if (facets == null || facets.isEmpty()) return Collections.emptyMap();

        StringBuilder unionSql = new StringBuilder();
        List<Object>  params   = new ArrayList<>();
        boolean       first    = true;

        for (String facet : facets) {
            String col = FACET_COL_MAP.get(facet);
            if (col == null) {
                log.warn("Unsupported facet: {} — skipping", facet);
                continue;
            }

            if (!first) unionSql.append(" UNION ALL ");

            // Build WHERE conditions excluding this facet's own filter
            StringBuilder conditions = new StringBuilder();
            buildConditions(conditions, params, filters, facet);

            unionSql.append(String.format(FACET_SQL, facet, col,
                    conditions.toString(), col));

            first = false;
        }

        if (unionSql.isEmpty()) return Collections.emptyMap();

        log.debug("Facet SQL: {}", unionSql);

        // Execute UNION ALL — one DB round trip for all facets
        Map<String, Object> result = new LinkedHashMap<>();

        jdbc.query(unionSql.toString(), rs -> {
            String facetName = rs.getString("facet");
            String value     = rs.getString("value");
            long   count     = rs.getLong("count");

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", value);
            entry.put("count", count);

            ((List<Map<String, Object>>) result
                    .computeIfAbsent(facetName, k -> new ArrayList<>()))
                    .add(entry);

        }, params.toArray());

        return result;
    }

    /**
     * Builds WHERE conditions for one facet block.
     * Excludes the current facet's own filter (disjunctive).
     * All values are bind params — safe from SQL injection.
     */
    private void buildConditions(StringBuilder conditions,
                                  List<Object> params,
                                  Map<String, Object> filters,
                                  String excludeFacet) {
        if (filters == null) return;

        // Exact match filters — excluded for their own facet block
        addCondition(conditions, params, filters, excludeFacet,
                "status",      "status",      "status = ?");
        addCondition(conditions, params, filters, excludeFacet,
                "contextType", "contextType", "contexttype = ?");
        addCondition(conditions, params, filters, excludeFacet,
                "orgId",       "orgId",       "orgid = ?");
        addCondition(conditions, params, filters, excludeFacet,
                "createdBy",   "createdBy",   "createdby = ?");

        // Date filters always applied to all facet blocks
        if (filters.containsKey("startDateFrom")) {
            conditions.append(" AND createdat >= ?::timestamptz");
            params.add(filters.get("startDateFrom").toString());
        }
        if (filters.containsKey("startDateTo")) {
            conditions.append(" AND createdat <= ?::timestamptz");
            params.add(filters.get("startDateTo").toString());
        }
    }

    private void addCondition(StringBuilder conditions,
                               List<Object> params,
                               Map<String, Object> filters,
                               String excludeFacet,
                               String facetKey,
                               String filterKey,
                               String sqlCondition) {
        if (!excludeFacet.equals(facetKey)
                && filters.containsKey(filterKey)
                && filters.get(filterKey) != null) {
            conditions.append(" AND ").append(sqlCondition);
            params.add(filters.get(filterKey).toString());
        }
    }
}
