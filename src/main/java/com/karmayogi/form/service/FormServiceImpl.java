package com.karmayogi.form.service;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.entity.Form;
import com.karmayogi.form.repository.FormRepository;
import com.karmayogi.form.repository.FormSearchSpecification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.karmayogi.form.utils.Constants.*;
/**
 * @author anil
 */
@Service
@RequiredArgsConstructor
public class FormServiceImpl implements FormService{
    private static final Logger log = LoggerFactory.getLogger(FormServiceImpl.class);

    private final FormRepository formRepository;
    private final FormConfig formConfig;

    @Override
    public Map<String, Object> searchForms(Map<String, Object> request) {

        Map<String, Object> filters = (Map<String, Object>) request.getOrDefault(FILTERS, new HashMap<>());
        String       query     = (String) request.get(QUERY);
        int          page      = toInt(request.get(PAGE), 0);
        int          size      = toInt(request.get(SIZE), 20);
        String       sortBy    = (String) request.getOrDefault(SORT_BY, DEFAULT_SORT_BY);
        String       sortOrder = (String) request.getOrDefault(SORT_ORDER, DEFAULT_SORT_ORDER);
        List<String> facets    = (List<String>) request.get(FACETS);

        log.debug(LOG_SEARCH_FORMS, filters, query, page, size, facets);
        Map<String, Object> response = new LinkedHashMap<>();
        List<String> unknownFilters = new ArrayList<>();
        if (filters != null) {
            filters.keySet().forEach(key -> {
                if (!formConfig.getFilterFieldMap().containsKey(key)
                        && !formConfig.getDateFromMap().containsKey(key)
                        && !formConfig.getDateToMap().containsKey(key)) {
                    unknownFilters.add(key);
                    log.warn(LOG_UNKNOWN_FILTER, key);
                }
            });
        }

        if (!unknownFilters.isEmpty()) {
            log.warn(LOG_UNKNOWN_FILTERS, unknownFilters);
            response.put(WARNINGS, UNKNOWN_FILTERS_WARNING + unknownFilters);
            return response;
        }

        Specification<Form> spec     = new FormSearchSpecification(filters, query,  formConfig.getFilterFieldMap(),
                formConfig.getDateFromMap(),
                formConfig.getDateToMap());
        Pageable            pageable = buildPageable(page, size, sortBy, sortOrder);

        Page<Form> result = formRepository.findAll(spec, pageable);
        response.put(COUNT,   result.getTotalElements());
        response.put(CONTENT, result.getContent()
                                            .stream()
                                            .map(this::toFormSummary)
                                            .collect(Collectors.toList()));

        if (facets != null && !facets.isEmpty()) {
            response.put(FACETS,
                    formRepository.getFacets(filters, facets));
        }

        return response;
    }

    private Pageable buildPageable(int page, int size,
                                   String sortBy, String sortOrder) {
        String field = formConfig.getAllowedSort().contains(sortBy)
                ? sortBy : DEFAULT_SORT_BY;
        Sort.Direction dir = ASC.equalsIgnoreCase(sortOrder)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        if (size == 0) return PageRequest.of(0, 1, Sort.by(dir, field));
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private int toInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val.toString()); }
        catch (Exception e) { return defaultVal; }
    }

    private Map<String, Object> toFormSummary(Form form) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put(FORM_ID,      form.getFormId());
        summary.put(TITLE,        form.getTitle());
        summary.put(STATUS,       form.getStatus());
        summary.put(CONTEXT_TYPE, form.getContextType());
        summary.put(VERSION,      form.getVersion());
        summary.put(CREATED_AT,   form.getCreatedAt());
        summary.put(UPDATED_AT,   form.getUpdatedAt());
        summary.put(CREATED_BY,   form.getCreatedBy());
        summary.put(ORG_ID,       form.getOrgId());
        summary.put(ORG_NAME,     form.getOrgName());
        summary.put(START_DATE,   form.getStartDate());
        summary.put(END_DATE,     form.getEndDate());
        summary.put(BATCH_ID,     form.getBatchId());
        summary.put(COURSE_ID,    form.getCourseId());
        return summary;
    }
}
