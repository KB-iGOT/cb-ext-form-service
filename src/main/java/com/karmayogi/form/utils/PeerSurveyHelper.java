package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.config.cassandrautils.CassandraOperation;
import com.karmayogi.form.entity.Forms;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.model.SearchCriteria;
import com.karmayogi.form.repository.FormQuestionsRepository;
import com.karmayogi.form.repository.FormRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.global.ParsedGlobal;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class PeerSurveyHelper {

    private static final Logger log = LoggerFactory.getLogger(PeerSurveyHelper.class);

    private final FormRepository formsJpaRepository;
    private final ObjectMapper objectMapper;
    private final CassandraOperation cassandraOperation;
    private final FormQuestionsRepository formQuestionsRepository;
    private final FormConfig formConfig;

    public boolean peerSurveyExists(String identifier, String orgId,
                                    List<String> statuses) {
        List<Forms> existing = formsJpaRepository.findExistingPeerSurveys(
                Constants.PEER_VALIDATION_SURVEY, orgId, identifier, statuses);
        return CollectionUtils.isNotEmpty(existing);
    }

    public List<FieldMeta> getPeerEvaluationCommonQuestions() {
        try {
            Map<String, Object> queryParams = Collections.singletonMap(
                    Constants.ID, Constants.PEER_VALIDATION_SURVEY);
            List<Map<String, Object>> defaultQuestions =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            Constants.KEYSPACE_SUNBIRD,
                            Constants.SYSTEM_SETTINGS,
                            queryParams, null, null);

            if (CollectionUtils.isEmpty(defaultQuestions)) return Collections.emptyList();

            String value = (String) defaultQuestions.get(0).get(Constants.VALUE);
            return Arrays.asList(objectMapper.readValue(value, FieldMeta[].class));

        } catch (Exception e) {
            log.error("getPeerEvaluationCommonQuestions failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }


    public void mergeSystemGeneratedQuestions(FormRequest request) {
        List<FieldMeta> commonQuestions = getPeerEvaluationCommonQuestions();
        if (CollectionUtils.isEmpty(commonQuestions)) return;

        List<FieldMeta> existingFields = request.getFields();
        if (existingFields == null) existingFields = new ArrayList<>();

        int maxOrder = existingFields.stream()
                .map(FieldMeta::getOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        for (FieldMeta field : commonQuestions) {
            maxOrder++;
            field.setOrder(maxOrder);
            existingFields.add(field);
        }
        request.setFields(existingFields);
    }


    public Long parseEndDate(String endDate) {
        try {
            return Instant.parse(endDate).toEpochMilli();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid endDate format");
        }
    }


    public String validatePeerSurveyUpdate(FormRequest form) {
        if (form == null) return "Request is required";
        if (StringUtils.isBlank(form.getTitle()))
            return Constants.ERR_TITLE_MISSING;
        if (form.getClientVersion() == null || form.getClientVersion() <= 0.0f)
            return Constants.ERR_INVALID_CLIENT_VERSION;

        int triggerAfter = 30;
        Map<String, Object> additionalProps = form.getAdditionalProperties();
        if (MapUtils.isNotEmpty(additionalProps)) {
            Object triggerObj = additionalProps.get(Constants.TRIGGER_AFTER);
            if (triggerObj != null) {
                try {
                    triggerAfter = Integer.parseInt(triggerObj.toString());
                    if (triggerAfter < 30 || triggerAfter > 60 || triggerAfter % 5 != 0)
                        return Constants.PEER_SURVEY_TRIGGER_AFTER_RANGE_INVALID;
                } catch (Exception e) {
                    return Constants.PEER_SURVEY_TRIGGER_AFTER_INVALID;
                }
            }
            Object lookBackObj = additionalProps.get(Constants.COMPLETION_LOOK_BACK);
            if (lookBackObj != null && triggerObj != null) {
                try {
                    int lookBack = Integer.parseInt(lookBackObj.toString());
                    if (lookBack < triggerAfter)
                        return Constants.PEER_SURVEY_LOOKBACK_LESS_THAN_TRIGGER;
                } catch (Exception e) {
                    return Constants.PEER_SURVEY_LOOKBACK_INVALID;
                }
            }
        } else {
            return Constants.PEER_SURVEY_INVALID_ADDITIONAL_PROPERTY;
        }

        if (StringUtils.isBlank(form.getEndDate()))
            return Constants.PEER_SURVEY_END_DATE_MISSING;

        try {
            Instant endDate     = Instant.parse(form.getEndDate());
            Instant minAllowed  = Instant.now().plus(triggerAfter,
                    java.time.temporal.ChronoUnit.DAYS);
            if (endDate.isBefore(minAllowed))
                return Constants.PEER_SURVEY_END_DATE_BEFORE_ALLOWED;
        } catch (Exception e) {
            return Constants.PEER_SURVEY_END_DATE_INVALID;
        }

        List<com.karmayogi.form.model.FieldMeta> fields = form.getFields();
        if (!org.apache.commons.collections4.CollectionUtils.isEmpty(fields)) {
            long customCount = fields.stream()
                    .filter(f -> !Boolean.TRUE.equals(
                            org.apache.commons.collections4.MapUtils.getBoolean(
                                    f.getAdditionalProperties(),
                                    Constants.SYSTEM_GENERATED)))
                    .count();
            if (customCount > 2)
                return Constants.PEER_SURVEY_MAX_ADDITIONAL_QUESTIONS;

            for (com.karmayogi.form.model.FieldMeta field : fields) {
                if (StringUtils.isBlank(field.getFieldType()))
                    return Constants.PEER_SURVEY_FIELD_TYPE_MISSING;
                String fieldType = field.getFieldType().toLowerCase();
                if (!fieldType.equals(Constants.RADIO)
                        && !fieldType.equals(Constants.CHECKBOX)
                        && !fieldType.equals(Constants.TEXTAREA)
                        && !fieldType.equals(Constants.NUMERIC_RATING))
                    return Constants.PEER_SURVEY_FIELD_TYPE_INVALID;
                if (StringUtils.isBlank(field.getName()))
                    return Constants.PEER_SURVEY_QUESTION_NAME_MISSING;
            }
        }
        return null;
    }

    public String validateActiveSurveyOverlap(String identifier, String orgId,
                                              long incomingStart, long incomingEnd,
                                              String currentFormId) {
        List<Forms> surveys =
                formsJpaRepository.findExistingPeerSurveys(
                        Constants.PEER_VALIDATION_SURVEY, orgId, identifier,
                        List.of("Draft", "Active"));

        for (Forms survey : surveys) {
            // skip current form
            if (currentFormId != null && currentFormId.equals(survey.getFormId()))
                continue;

            if ("Draft".equalsIgnoreCase(survey.getStatus()))
                return Constants.PEER_SURVEY_ALREADY_EXISTS;

            Long existingStart = survey.getCreatedAt();
            Long existingEnd   = survey.getEndDate();
            if (existingStart != null && existingEnd != null) {
                boolean overlap = existingStart <= incomingEnd
                        && existingEnd >= incomingStart;
                if (overlap) return Constants.PEER_SURVEY_ALREADY_EXISTS;
            }
        }
        return null;
    }

    public String validateSystemGeneratedProtection(FormRequest request) {
        List<FieldMeta> incomingFields = request.getFields();


        com.karmayogi.form.entity.Forms existingForm = formsJpaRepository.findById(request.getFormId()).orElse(null);
        if (existingForm == null)
            return Constants.ERR_FORM_NOT_FOUND;

        List<FieldMeta> existingFields;


        if (existingForm.getClientVersion() != null
                && existingForm.getClientVersion() >= formConfig.getV2ClientVersion()) {
            List<Map<String, Object>> questions = (List<Map<String, Object>>) existingForm.getQuestions();
            existingFields = CollectionUtils.isEmpty(questions)
                    ? Collections.emptyList()
                    : questions.stream()
                    .map(q -> objectMapper.convertValue(q, FieldMeta.class))
                    .toList();
        } else {
            existingFields = formQuestionsRepository
                    .findByFormIdOrderByQuestionOrderAsc(request.getFormId())
                    .stream()
                    .map(q -> {
                        FieldMeta f = new FieldMeta();
                        f.setId(q.getQuestionId());
                        f.setName(q.getName());
                        f.setFieldType(q.getFieldType());
                        f.setAdditionalProperties(q.getAdditionalProperties());
                        return f;
                    })
                    .toList();
        }

        long customCount = incomingFields.stream()
                .filter(f -> !Boolean.TRUE.equals(
                        MapUtils.getBoolean(f.getAdditionalProperties(),
                                Constants.SYSTEM_GENERATED)))
                .count();
        if (customCount > 2)
            return "Maximum 2 additional questions allowed";

        for (FieldMeta existing : existingFields) {
            boolean isSystem = Boolean.TRUE.equals(
                    MapUtils.getBoolean(existing.getAdditionalProperties(),
                            Constants.SYSTEM_GENERATED));
            if (!isSystem) continue;

            FieldMeta incoming = incomingFields.stream()
                    .filter(f -> existing.getId().equals(f.getId()))
                    .findFirst().orElse(null);

            if (incoming == null)
                return "System generated questions cannot be removed";
            if (!existing.getName().equals(incoming.getName())
                    || !existing.getFieldType().equals(incoming.getFieldType()))
                return "System generated questions cannot be modified";
        }
        return null;
    }

    public String fetchRecordsFromDB(String keyspace, String tableName, Map<String, Object> queryParams, List<String> fieldNames) {
        try {
            List<Map<String, Object>> records =
                    cassandraOperation.getRecordsByPropertiesByKey(
                            keyspace,
                            tableName,
                            queryParams,
                            fieldNames,
                            null);
            if (CollectionUtils.isEmpty(records)) return null;
            return (String) records.get(0).get(fieldNames.get(0));
        } catch (Exception e) {
            log.warn("fetchOrgName failed: {}", e.getMessage());
            return null;
        }
    }

    public String validatePeerSurveySearch(SearchCriteria criteria, List<String> orgIds) {
        if (CollectionUtils.isEmpty(orgIds))
            return Constants.PEER_SURVEY_ORG_ID_REQUIRED;
        if (criteria == null)
            return Constants.INVALID_REQUEST;
        if (criteria.getPage() != null && criteria.getPage() > 100)
            return Constants.MAX_PAGE_EXCEEDED;
        if (criteria.getSize() != null && criteria.getSize() > 50)
            return Constants.MAX_SIZE_EXCEEDED;
        if (StringUtils.isNotBlank(criteria.getQuery())
                && criteria.getQuery().length() > 100)
            return Constants.INVALID_QUERY_LENGTH;
        return null;
    }

    public BoolQueryBuilder buildFacetQuery(List<String> orgIds, boolean isSpvUser) {

        BoolQueryBuilder facetQuery = QueryBuilders.boolQuery();

        facetQuery.must(
                QueryBuilders.termQuery(Constants.CONTEXT_TYPE, Constants.PEER_VALIDATION_SURVEY)
        );

        if (!isSpvUser) {

            BoolQueryBuilder nestedFacetQuery = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termsQuery("createdFor.orgId", orgIds));

            facetQuery.must(
                    QueryBuilders.nestedQuery("createdFor", nestedFacetQuery, ScoreMode.None)
            );
        }

        return facetQuery;
    }

    public AggregationBuilder buildOrgFacetAggregation() {

        return AggregationBuilders.global("org_global")
                .subAggregation(
                        AggregationBuilders.nested("org_nested", "createdFor")
                                .subAggregation(
                                        AggregationBuilders.terms("orgIdFacet")
                                                .field("createdFor.orgId")
                                                .size(100)
                                                .subAggregation(
                                                        AggregationBuilders.terms("orgNameFacet")
                                                                .field("createdFor.orgName.keyword")
                                                                .size(1)
                                                )
                                )
                );
    }

    public List<Map<String, Object>> parseOrgFacets(SearchResponse searchResponse) {

        List<Map<String, Object>> orgFacetList = new ArrayList<>();

        ParsedGlobal global = searchResponse.getAggregations().get("org_global");
        if (global == null) return orgFacetList;

        ParsedNested nested = global.getAggregations().get("org_nested");
        if (nested == null) return orgFacetList;

        Terms orgTerms = nested.getAggregations().get("orgIdFacet");

        for (Terms.Bucket bucket : orgTerms.getBuckets()) {

            String orgId = bucket.getKeyAsString();
            long count = bucket.getDocCount();

            Terms orgNameTerms = bucket.getAggregations().get("orgNameFacet");

            String orgName = "";

            if (orgNameTerms != null && !orgNameTerms.getBuckets().isEmpty()) {
                orgName = orgNameTerms.getBuckets().get(0).getKeyAsString();
            }

            Map<String, Object> obj = new HashMap<>();
            obj.put("orgId", orgId);
            obj.put("orgName", orgName);
            obj.put("count", count);

            orgFacetList.add(obj);
        }

        return orgFacetList;
    }

    public SearchSourceBuilder buildPeerSurveySourceBuilder(SearchCriteria criteria,
                                                             List<String> orgIds,
                                                             Boolean isSpvSearch) {
        BoolQueryBuilder queryBuilder = buildPeerSurveyQuery(criteria, orgIds);

        int page = criteria.getPage() != null ? criteria.getPage() : 0;
        int size = criteria.getSize() != null ? criteria.getSize() : 10;

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(queryBuilder)
                .from(page * size)
                .size(size);

        if (StringUtils.isNotBlank(criteria.getSortBy())) {
            SortOrder sortOrder = Constants.DESC.equalsIgnoreCase(criteria.getSortOrder())
                    ? SortOrder.DESC : SortOrder.ASC;
            sourceBuilder.sort(criteria.getSortBy(), sortOrder);
        }

        BoolQueryBuilder facetQuery = buildFacetQuery(orgIds, isSpvSearch);
        TermsAggregationBuilder statusAgg = AggregationBuilders
                .terms("statusFacet").field("status").size(10);
        sourceBuilder.aggregation(AggregationBuilders
                .filter("status_filter", facetQuery).subAggregation(statusAgg));

        if (Boolean.TRUE.equals(isSpvSearch))
            sourceBuilder.aggregation(buildOrgFacetAggregation());

        return sourceBuilder;
    }

    public BoolQueryBuilder buildPeerSurveyQuery(SearchCriteria criteria,
                                                  List<String> orgIds) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery(Constants.CONTEXT_TYPE,
                        Constants.PEER_VALIDATION_SURVEY))
                .must(QueryBuilders.nestedQuery("createdFor",
                        QueryBuilders.boolQuery().must(
                                QueryBuilders.termsQuery("createdFor.orgId", orgIds)),
                        ScoreMode.None));

        if (StringUtils.isNotBlank(criteria.getQuery())) {
            String keyword = criteria.getQuery();
            queryBuilder.must(QueryBuilders.boolQuery()
                    .should(QueryBuilders.matchPhraseQuery("title", keyword).boost(5))
                    .should(QueryBuilders.matchQuery("title", keyword).boost(3))
                    .should(QueryBuilders.termQuery("title.keyword", keyword).boost(10))
                    .minimumShouldMatch(1));
        }

        applyFilters(queryBuilder, criteria.getFilters());
        return queryBuilder;
    }

    private void applyFilters(BoolQueryBuilder queryBuilder,
                              Map<String, Object> filters) {
        if (MapUtils.isEmpty(filters)) return;

        if (filters.containsKey("status")) {
            Object status = filters.get("status");
            if (status instanceof List)
                queryBuilder.must(QueryBuilders.termsQuery("status", (List<?>) status));
            else
                queryBuilder.must(QueryBuilders.termQuery("status", status));
        }

        Object startFrom = filters.get("startDateFrom");
        Object endTo = filters.get("endDateTo");
        if (startFrom != null && endTo != null) {
            long filterEnd = Long.parseLong(endTo.toString()) + (24L * 60 * 60 * 1000) - 1;
            queryBuilder.must(QueryBuilders.boolQuery()
                    .must(QueryBuilders.rangeQuery("startDate")
                            .gte(Long.parseLong(startFrom.toString())))
                    .must(QueryBuilders.rangeQuery("endDate").lte(filterEnd)));
        }
    }

    public Map<String, Object> buildPeerSurveyResponse(SearchResponse searchResponse, Boolean isSpvSearch) {
        List<Map<String, Object>> surveys = Arrays.stream(
                        searchResponse.getHits().getHits())
                .map(hit -> {
                    Map<String, Object> survey = hit.getSourceAsMap();
                    survey.put("id", hit.getId());
                    return survey;
                })
                .collect(Collectors.toList());

        Filter statusFilter = searchResponse.getAggregations().get("status_filter");
        Terms statusFacet = statusFilter.getAggregations().get("statusFacet");
        Map<String, Long> statusFacets = statusFacet.getBuckets().stream()
                .collect(Collectors.toMap(
                        Terms.Bucket::getKeyAsString,
                        Terms.Bucket::getDocCount));

        Map<String, Object> facets = new HashMap<>();
        facets.put("status", statusFacets);
        if (Boolean.TRUE.equals(isSpvSearch))
            facets.put("orgNames", parseOrgFacets(searchResponse));

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.COUNT, searchResponse.getHits().getTotalHits());
        response.put(Constants.CONTENT, surveys);
        response.put("facets", facets);
        return response;
    }

    public String validatePublishPeerSurvey(Forms survey) {
        Map<String, Object> additionalProps = survey.getAdditionalProperties();

        Object triggerObj = additionalProps != null ? additionalProps.get(Constants.TRIGGER_AFTER) : null;
        String identifier = additionalProps != null ? (String) additionalProps.get(Constants.IDENTIFIER) : null;

        if (triggerObj == null)
            return "Trigger after value missing";
        if (identifier == null)
            return "identifier is missing";

        int triggerAfter;
        try {
            triggerAfter = Integer.parseInt(triggerObj.toString());
        } catch (Exception e) {
            return "Invalid trigger after value";
        }

        if (survey.getEndDate() == null)
            return "End date missing";

        Instant endDate    = Instant.ofEpochMilli(survey.getEndDate());
        Instant minAllowed = Instant.now().plus(triggerAfter, java.time.temporal.ChronoUnit.DAYS);
        if (endDate.isBefore(minAllowed))
            return "End date must be greater than current date + triggerAfter days";

        return null;
    }
}
