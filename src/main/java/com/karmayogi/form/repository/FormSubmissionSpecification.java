package com.karmayogi.form.repository;

import com.karmayogi.form.entity.FormSubmission;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author anil
 */

public class FormSubmissionSpecification {

    private FormSubmissionSpecification() {}

    public static Specification<FormSubmission> fromFilters(Map<String, Object> filters) {
        return (root, query, cb) -> {
            if (filters == null || filters.isEmpty()) {
                return cb.conjunction();
            }
            List<Predicate> predicates = filters.entrySet().stream()
                    .filter(e -> !isDateKey(e.getKey()))
                    .filter(e -> toFieldName(e.getKey()) != null)
                    .map(e -> buildPredicate(root, cb, e.getKey(), e.getValue()))
                    .filter(Objects::nonNull)
                    .toList();
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildPredicate(Root<FormSubmission> root,
                                            CriteriaBuilder cb,
                                            String key, Object value) {
        String fieldName = toFieldName(key);
        if (value instanceof List<?> list) {
            return root.get(fieldName).in(list);
        } else if (value instanceof String str) {
            if (str.contains("*")) {
                return cb.like(cb.lower(root.get(fieldName)),
                        str.replace("*", "%").toLowerCase());
            }
            return cb.equal(root.get(fieldName), str);
        } else if (value != null) {
            return cb.equal(root.get(fieldName), value);
        }
        return null;
    }


    private static String toFieldName(String key) {
        return switch (key) {
            case "formId"        -> "formId";
            case "status"        -> "status";
            case "submittedBy"   -> "submittedBy";
            case "updatedBy"     -> "updatedBy";
            case "contextId"     -> "contextId";
            case "contextType"   -> "contextType";
            case "contextOrgId"  -> "contextOrgId";
            case "userId"        -> "userId";
            case "version"       -> "version";
            default              -> null;
        };
    }

    public static boolean isDateKey(String key) {
        return "startDateFrom".equals(key) || "startDateTo".equals(key)
                || "endDateFrom".equals(key)   || "endDateTo".equals(key)
                || "submittedDateFrom".equals(key) || "submittedDateTo".equals(key);
    }

}

