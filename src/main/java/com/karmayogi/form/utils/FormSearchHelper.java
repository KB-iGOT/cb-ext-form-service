package com.karmayogi.form.utils;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.SearchCriteria;
import com.karmayogi.form.model.SearchPageRequest;
import com.karmayogi.form.model.SearchResultData;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.karmayogi.form.utils.Constants.*;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class FormSearchHelper {

    private static final Logger log = LoggerFactory.getLogger(FormSearchHelper.class);

    private final FormConfig formConfig;
    private final FormQuestionsRepository formQuestionsRepository;
    private final FormSubmissionRepository formSubmissionRepository;
    private final RestHighLevelClient esClient;



    public String validateFilters(Map<String, Object> filters) {
        if (MapUtils.isEmpty(filters)) return null;
        List<String> invalid = filters.keySet().stream()
                .filter(k -> !formConfig.getSearchAllowedFilterKeys().contains(k))
                .toList();
        return invalid.isEmpty() ? null
                : "Unsupported filter fields: " + String.join(", ", invalid);
    }


    public BoolQueryBuilder buildQuery(SearchCriteria criteria) {
        BoolQueryBuilder query = QueryBuilders.boolQuery();

        boolean contextTypePassed = criteria.getFilters() != null
                && criteria.getFilters().containsKey(Constants.CONTEXT_TYPE);
        if (!contextTypePassed && StringUtils.isBlank(criteria.getContextType())) {
            for (String ctx : formConfig.getSearchSupportedContextTypes()) {
                query.should(QueryBuilders.termQuery(Constants.CONTEXT_TYPE, ctx));
            }
            query.minimumShouldMatch(1);
        } else if (StringUtils.isNotBlank(criteria.getContextType())) {
            query.must(QueryBuilders.termQuery(Constants.CONTEXT_TYPE,
                    criteria.getContextType()));
        }

        if (StringUtils.isNotBlank(criteria.getQuery())) {
            String keyword = criteria.getQuery().trim();
            BoolQueryBuilder relevanceQuery = QueryBuilders.boolQuery()
                    .should(QueryBuilders.termQuery("title.keyword", keyword).boost(10f))
                    .should(QueryBuilders.matchPhraseQuery("title", keyword).boost(6f))
                    .should(QueryBuilders.matchQuery("title", keyword).boost(4f))
                    .should(QueryBuilders.matchQuery("title.prefix", keyword).boost(1f))
                    .should(org.elasticsearch.index.query.QueryBuilders.nestedQuery(
                            "createdFor",
                            QueryBuilders.boolQuery()
                                    .should(QueryBuilders.termQuery("createdFor.orgName.keyword", keyword).boost(8f))
                                    .should(QueryBuilders.matchPhraseQuery("createdFor.orgName", keyword).boost(5f))
                                    .should(QueryBuilders.matchQuery("createdFor.orgName", keyword).boost(3f))
                                    .should(QueryBuilders.matchQuery("createdFor.orgName.prefix", keyword).boost(1f)),
                            org.apache.lucene.search.join.ScoreMode.Max))
                    .minimumShouldMatch(1);
            query.must(relevanceQuery);
        }

        if (MapUtils.isNotEmpty(criteria.getFilters())) {
            applyFilters(query, criteria.getFilters());
        }

        return query;
    }

    private void applyFilters(BoolQueryBuilder query, Map<String, Object> filters) {
        Object startFrom = filters.get("startDateFrom");
        Object startTo   = filters.get("startDateTo");
        if (startFrom != null || startTo != null) {
            RangeQueryBuilder range = QueryBuilders.rangeQuery("createdDate");
            if (startFrom != null) range.gte(startFrom);
            if (startTo != null)   range.lte(startTo);
            query.must(range);
        }

        Object endFrom = filters.get("endDateFrom");
        Object endTo   = filters.get("endDateTo");
        if (endFrom != null || endTo != null) {
            RangeQueryBuilder range = QueryBuilders.rangeQuery("endDate");
            if (endFrom != null) range.gte(endFrom);
            if (endTo != null)   range.lte(endTo);
            query.must(range);
        }

        // term filters — skip date range keys already handled
        Set<String> dateKeys = Set.of("startDateFrom", "startDateTo",
                "endDateFrom", "endDateTo");
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (dateKeys.contains(entry.getKey())) continue;
            if (entry.getValue() instanceof String value) {
                if (value.contains("*")) {
                    query.must(QueryBuilders.wildcardQuery(entry.getKey(), value));
                } else {
                    query.must(QueryBuilders.termQuery(entry.getKey(), value));
                }
            } else {
                query.must(QueryBuilders.termQuery(entry.getKey(), entry.getValue()));
            }
        }
    }

    public Map<String, List<Map<String, Object>>> fetchFieldsFromEs(
            Set<String> formIds, int pageSize) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        try {
            int totalFieldsSize = pageSize * formConfig.getMaxFields();

            org.elasticsearch.index.query.BoolQueryBuilder query =
                    QueryBuilders.boolQuery()
                            .must(QueryBuilders.termsQuery(FORM_ID,
                                    formIds.toArray()))
                            .mustNot(QueryBuilders.termsQuery(Constants.CONTEXT_TYPE,
                                    formConfig.getSearchSupportedContextTypes()
                                            .toArray()));

            org.elasticsearch.search.builder.SearchSourceBuilder sourceBuilder =
                    new org.elasticsearch.search.builder.SearchSourceBuilder()
                            .query(query)
                            .size(totalFieldsSize);

            org.elasticsearch.action.search.SearchResponse response =
                    esClient.search(
                            new org.elasticsearch.action.search.SearchRequest(
                                    formConfig.getSearchIndex()).source(sourceBuilder),
                            org.elasticsearch.client.RequestOptions.DEFAULT);

            for (org.elasticsearch.search.SearchHit hit :
                    response.getHits().getHits()) {
                Map<String, Object> source = hit.getSourceAsMap();
                String formId = String.valueOf(source.get(FORM_ID));
                source.put(Constants.QUESTION_ID, hit.getId());
                result.computeIfAbsent(formId, k -> new ArrayList<>()).add(source);
            }
        } catch (Exception e) {
            log.error("fetchFieldsFromEs error: {}", e.getMessage(), e);
        }
        return result;
    }

    public Map<String, Long> fetchSubmissionCounts(Set<String> formIds) {
        Map<String, Long> result = new HashMap<>();
        if (formIds.isEmpty()) return result;
        try {
            formSubmissionRepository.countByFormIds(formIds)
                    .forEach(row -> result.put(
                            String.valueOf(row[0]),
                            ((Number) row[1]).longValue()));
        } catch (Exception e) {
            log.error("fetchSubmissionCounts error: {}", e.getMessage(), e);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public void flattenMandatoryFields(Map<String, Object> form) {
        List<Map<String, Object>> raw =
                (List<Map<String, Object>>) form.getOrDefault(
                        Constants.MANDATORY_FIELDS, Collections.emptyList());
        if (CollectionUtils.isEmpty(raw)) return;

        List<Map<String, Object>> flattened = new ArrayList<>();
        for (Map<String, Object> field : raw) {
            if (MapUtils.isEmpty(field)) continue;
            Map<String, Object> flat = new HashMap<>(field);
            Object additionalProps = flat.remove(Constants.ADDITIONAL_PROPERTIES);
            if (additionalProps instanceof Map) {
                flat.putAll((Map<String, Object>) additionalProps);
            }
            flattened.add(flat);
        }
        form.put(Constants.MANDATORY_FIELDS, flattened);
    }

    public SearchSourceBuilder buildSourceBuilder(SearchCriteria criteria,
                                                  BoolQueryBuilder queryBuilder, int page, int size) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(queryBuilder)
                .from(page * size)
                .size(size)
                .timeout(new TimeValue(formConfig.getEsTimeoutSeconds(), TimeUnit.SECONDS));

        // source filtering
        if (CollectionUtils.isNotEmpty(criteria.getFields())) {
            List<String> formFields = criteria.getFields().stream()
                    .filter(f -> !Constants.FIELDS.equals(f)
                            && !Constants.MANDATORY_FIELDS.equals(f))
                    .toList();
            if (!formFields.isEmpty()) {
                sourceBuilder.fetchSource(formFields.toArray(new String[0]), null);
            }
        }

        // sort
        if (StringUtils.isNotBlank(criteria.getSortBy())) {
            SortOrder sortOrder = Constants.DESC.equalsIgnoreCase(criteria.getSortOrder())
                    ? SortOrder.DESC : SortOrder.ASC;
            sourceBuilder.sort(criteria.getSortBy(), sortOrder);
        }
        return sourceBuilder;
    }

    public org.elasticsearch.action.search.SearchResponse executeSearch(
            org.elasticsearch.search.builder.SearchSourceBuilder sourceBuilder) throws IOException {
        return esClient.search(
                new org.elasticsearch.action.search.SearchRequest(
                        formConfig.getSearchIndex()).source(sourceBuilder),
                org.elasticsearch.client.RequestOptions.DEFAULT);
    }

    public SearchPageRequest validateAndBuildPageRequest(
            SearchCriteria criteria) {

        int page = criteria.getPage() != null && criteria.getPage() >= 0
                ? criteria.getPage()
                : 0;

        if (page > formConfig.getSearchMaxPage()) {
            return new SearchPageRequest(
                    page,
                    0,
                    "Maximum allowed page number is "
                            + formConfig.getSearchMaxPage());
        }

        int size = criteria.getSize() != null && criteria.getSize() > 0
                ? criteria.getSize()
                : formConfig.getSearchDefaultPageSize();

        if (size > formConfig.getSearchMaxPageSize()) {
            return new SearchPageRequest(
                    page,
                    size,
                    "Maximum allowed page size is "
                            + formConfig.getSearchMaxPageSize());
        }

        return new SearchPageRequest(page, size, null);
    }

    public org.elasticsearch.action.search.SearchResponse search(
            SearchCriteria criteria,
            SearchPageRequest pageRequest) throws IOException {

        BoolQueryBuilder queryBuilder = buildQuery(criteria);

        SearchSourceBuilder sourceBuilder =
                buildSourceBuilder(
                        criteria,
                        queryBuilder,
                        pageRequest.page(),
                        pageRequest.size());

        return executeSearch(sourceBuilder);
    }

    public SearchResultData extractSearchResults(
            SearchResponse searchResponse) {

        List<Map<String, Object>> forms = new ArrayList<>();
        Set<String> formIds = new LinkedHashSet<>();

        for (SearchHit hit : searchResponse.getHits().getHits()) {

            Map<String, Object> source = hit.getSourceAsMap();

            if (source.get(FORM_ID) == null) {
                continue;
            }

            Map<String, Object> formResult =
                    new LinkedHashMap<>(source);

            formResult.put(ID, hit.getId());

            formIds.add(source.get(FORM_ID).toString());

            forms.add(formResult);
        }

        return new SearchResultData(forms, formIds);
    }

    public List<Map<String, Object>> enrichResults(
            List<Map<String, Object>> forms,
            Set<String> formIds,
            SearchCriteria criteria,
            int size) {

        boolean includeFields =
                criteria.getFields() == null
                        || criteria.getFields().contains(FIELDS);

        boolean includeMandatoryFields =
                criteria.getFields() == null
                        || criteria.getFields().contains(MANDATORY_FIELDS);

        Map<String, List<Map<String, Object>>> formFieldsMap =
                (includeFields && !formIds.isEmpty())
                        ? fetchFieldsFromEs(formIds, size)
                        : Collections.emptyMap();

        Map<String, Long> submissionCounts =
                fetchSubmissionCounts(formIds);

        List<Map<String, Object>> finalResults =
                new ArrayList<>();

        for (Map<String, Object> form : forms) {

            String formId =
                    String.valueOf(form.get(FORM_ID));

            String contextType =
                    String.valueOf(form.get(CONTEXT_TYPE));

            if (includeFields
                    && !ASSIGNMENT.equalsIgnoreCase(contextType)) {

                form.put(
                        FIELDS,
                        formFieldsMap.getOrDefault(
                                formId,
                                Collections.emptyList()));
            }

            if (includeMandatoryFields) {
                flattenMandatoryFields(form);
            }

            form.put(
                    SUBMISSION_COUNT,
                    submissionCounts.getOrDefault(formId, 0L));

            finalResults.add(form);
        }

        return finalResults;
    }

}
