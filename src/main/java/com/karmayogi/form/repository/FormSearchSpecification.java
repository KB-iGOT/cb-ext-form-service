package com.karmayogi.form.repository;

import com.karmayogi.form.entity.Form;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds dynamic WHERE clause from request filters.
 * All filter mappings injected from FormConfig — no hardcoding.
 * Add new filter = add one line in application.properties only.
 */
/**
 * @author anil
 */
public class FormSearchSpecification implements Specification<Form> {

    private final Map<String, Object> filters;
    private final String              query;
    private final Map<String, String> filterFieldMap;
    private final Map<String, String> dateFromMap;
    private final Map<String, String> dateToMap;

    public FormSearchSpecification(Map<String, Object> filters,
                                   String query,
                                   Map<String, String> filterFieldMap,
                                   Map<String, String> dateFromMap,
                                   Map<String, String> dateToMap) {
        this.filters        = filters;
        this.query          = query;
        this.filterFieldMap = filterFieldMap;
        this.dateFromMap    = dateFromMap;
        this.dateToMap      = dateToMap;
    }

    @Override
    public Predicate toPredicate(Root<Form> root,
                                 CriteriaQuery<?> cq,
                                 CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        if (filters != null) {

            filterFieldMap.forEach((filterKey, fieldName) -> {
                if (filters.containsKey(filterKey)
                        && filters.get(filterKey) != null) {
                    predicates.add(cb.equal(
                            root.get(fieldName),
                            filters.get(filterKey).toString()));
                }
            });


            dateFromMap.forEach((filterKey, fieldName) -> {
                if (filters.containsKey(filterKey)
                        && filters.get(filterKey) != null) {
                    predicates.add(cb.greaterThanOrEqualTo(
                            root.get(fieldName),
                            Instant.parse(filters.get(filterKey).toString())
                    ));
                }
            });


            dateToMap.forEach((filterKey, fieldName) -> {
                if (filters.containsKey(filterKey)
                        && filters.get(filterKey) != null) {
                    predicates.add(cb.lessThanOrEqualTo(
                            root.get(fieldName),
                            Instant.parse(filters.get(filterKey).toString())
                    ));
                }
            });
        }


        if (query != null && !query.isBlank()) {
            predicates.add(cb.like(
                    cb.lower(root.get("title")),
                    "%" + query.trim().toLowerCase() + "%"
            ));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}