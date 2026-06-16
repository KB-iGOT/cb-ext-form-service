package com.karmayogi.form.utils;

public class Constants {

    public static final String FILTERS    = "filters";
    public static final String QUERY      = "query";
    public static final String PAGE       = "page";
    public static final String SIZE       = "size";
    public static final String SORT_BY    = "sortBy";
    public static final String SORT_ORDER = "sortOrder";
    public static final String FACETS     = "facets";

    public static final String COUNT    = "count";
    public static final String CONTENT  = "content";
    public static final String FACETS_KEY = "facets";
    public static final String WARNINGS = "warnings";

    public static final String DEFAULT_SORT_BY    = "createdAt";
    public static final String DEFAULT_SORT_ORDER = "DESC";
    public static final int    DEFAULT_PAGE        = 0;
    public static final int    DEFAULT_SIZE        = 20;
    public static final String ASC = "ASC";

    public static final String UNKNOWN_FILTERS_WARNING =
            "Following filters are not supported and were ignored: ";


    public static final String LOG_UNKNOWN_FILTER  = "Unknown filter key ignored: {}";
    public static final String LOG_UNKNOWN_FILTERS = "Unknown filters ignored: {}";
    public static final String LOG_SEARCH_FORMS    =
            "searchForms: filters={} query={} page={} size={} facets={}";

    public static final String FORM_ID      = "formId";
    public static final String TITLE        = "title";
    public static final String STATUS       = "status";
    public static final String CONTEXT_TYPE = "contextType";
    public static final String VERSION      = "version";
    public static final String CREATED_AT   = "createdAt";
    public static final String UPDATED_AT   = "updatedAt";
    public static final String CREATED_BY   = "createdBy";
    public static final String ORG_ID       = "orgId";
    public static final String ORG_NAME     = "orgName";
    public static final String START_DATE   = "startDate";
    public static final String END_DATE     = "endDate";
    public static final String BATCH_ID     = "batchId";
    public static final String COURSE_ID    = "courseId";
    public static final String CLIENT_VERSION  = "clientVersion";
    public static final Double VERSION_2 = 2.0;
    public static final String LOG_FORM_ID_BLANK    = "getFormById called with blank formId";
    public static final String LOG_CACHE_HIT        = "getFormById CACHE HIT formId={}";
    public static final String LOG_FORM_NOT_FOUND   = "getFormById NOT FOUND formId={}";
    public static final String LOG_FORM_V1_FIELDS   = "getFormById formId={} version=1 fields={}";
    public static final String LOG_FORM_V2_JSONB    = "getFormById formId={} version=2 questions from JSONB";
    public static final String LOG_FORM_ERROR       = "getFormById error formId={}: {}";
    public static final String ERR_FORM_ID_REQUIRED = "formId is required";
    public static final String ERR_FORM_NOT_FOUND   = "Form not found: ";
    public static final String ERR_INTERNAL         = "Internal server error: ";
    public static final String FIELDS = "fields";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED  = "FAILED";
    public static final String RESPONSE       = "response";
    public static final String API_GET_FORM_BY_ID = "api.getFormById.v3";
    public static final String CREATED_DATE = "createdDate";
    public static final String UPDATED_DATE = "updatedDate";
    public static final String ARCHIVED_DATE = "archivedDate";
    public static final String UPDATED_BY = "updatedBy";
    public static final String ADDITIONAL_PROPERTIES  = "additionalProperties";
    public static final String MANDATORY_FIELDS = "mandatoryFields";
    public static final String META               = "meta";
    public static final String VALUES = "values";
    public static final String REFAPI = "refApi";
    public static final String LOGICAL_GROUP_CODE       = "logicalGroupCode";
    public static final String QUESTION_ID        = "id";
    public static final String NAME               = "name";
    public static final String FIELD_TYPE         = "fieldType";
    public static final String ORDER              = "order";
    public static final String IS_REQUIRED        = "isRequired";
    public static final String NOT_APPLICABLE     = "notApplicable";
    public static final String SECTION_ID         = "sectionId";
    public static final String PARENT_ID          = "parentId";
    public static final String HIDDEN             = "hidden";
    public static final String REF_API            = "refApi";
    public static final String QUESTION = "question";

    private Constants() {}
}
