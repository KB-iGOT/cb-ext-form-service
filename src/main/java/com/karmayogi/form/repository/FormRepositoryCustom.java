package com.karmayogi.form.repository;

import java.util.List;
import java.util.Map;

/**
 * Custom repository interface for facet aggregations.
 * Spring Data JPA picks up FormRepositoryImpl automatically.
 */
/**
 * @author anil
 */
public interface FormRepositoryCustom {
    Map<String, Object> getFacets(Map<String, Object> filters, List<String> facets);
}
