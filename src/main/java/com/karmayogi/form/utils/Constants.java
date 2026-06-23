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
    public static final Double VERSION_2 = 1.2;
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
    public static final String ERR_INVALID_CONTEXT_TYPE    = "Invalid or missing contextType";
    public static final String ERR_INVALID_USER_ID         = "Invalid or missing userId";
    public static final String ERR_CLIENT_VERSION_MISSING  = "clientVersion is required";
    public static final String ERR_INVALID_CLIENT_VERSION  = "clientVersion must be greater than 0";
    public static final String ERR_FIELD_MISSING           = "fields are required";
    public static final String ERR_FIELD_TYPE_MISSING      = "fieldType is required";
    public static final String ERR_INVALID_FIELD_TYPE      = "Invalid fieldType";
    public static final String ERR_FIELD_ORDER_MISSING     = "field order is required";
    public static final String ERR_INVALID_FIELD_ORDER     = "field order must be unique and greater than 0";
    public static final String ERR_TITLE_MISSING           = "Title is required";
    public static final String ERR_TITLE_TOO_LONG          = "Title must not exceed 255 characters";
    public static final String ERR_TITLE_CONTAINS_URL      = "Title should not contain URLs";
    public static final String ERR_FIELD_NAME_MISSING      = "Field name is required";
    public static final String ERR_FIELD_NAME_TOO_LONG     = "Field name must not exceed 500 characters";
    public static final String ERR_FIELD_NAME_CONTAINS_URL = "Field name should not contain URLs";
    public static final String ERR_INVALID_INPUT           = "Input contains invalid characters or script";
    public static final String ASSIGNMENT              = "assignment";
    public static final String PEER_VALIDATION_SURVEY  = "peerValidationSurvey";
    public static final String COMPLETION_SURVEY       = "completionSurvey";
    public static final String AGK_PUBLIC_SURVEY       = "AGKPublicSurvey";
    public static final String PROGRAM_COMPLETION_FORM = "program-completion-form";
    public static final String API_CREATE_FORM    = "api.createForm.v3";
    public static final String API_UPDATE_FORM    = "api.updateForm.v3";
    public static final String TOTAL_FIELDS = "totalFields";
    public static final String ERR_UNKNOWN_PROPERTY_KEY    = "Unknown property key: ";
    public static final String ERR_VALUE_KEY_INVALID       = "Invalid value key: ";
    public static final String ERR_VALUE_TEXT_INVALID      = "Invalid value text: ";
    public static final String PEER_SURVEY_TRIGGER_AFTER_INVALID = "Invalid triggerAfter value";
    public static final String PEER_SURVEY_TRIGGER_AFTER_RANGE_INVALID = "TriggerAfter must be between 30 and 60 with increment of 5";
    public static final String PEER_SURVEY_LOOKBACK_INVALID = "Invalid completionLookBack value";
    public static final String PEER_SURVEY_LOOKBACK_LESS_THAN_TRIGGER = "CompletionLookBack cannot be less than TriggerAfter";
    public static final String PEER_SURVEY_THUMBNAIL_MISSING = "Thumbnail is mandatory for peer validation survey";
    public static final String PEER_SURVEY_THUMBNAIL_INVALID = "Invalid thumbnail URL format";
    public static final String PEER_SURVEY_DURATION_MISSING = "Duration is mandatory for peer validation survey";
    public static final String PEER_SURVEY_END_DATE_MISSING = "End date is mandatory";
    public static final String PEER_SURVEY_END_DATE_INVALID = "Invalid EndDate format. Must be ISO format";
    public static final String PEER_SURVEY_END_DATE_BEFORE_ALLOWED = "EndDate must be greater than current date + triggerAfter days";
    public static final String PEER_SURVEY_MAX_ADDITIONAL_QUESTIONS = "Maximum 2 additional questions allowed";
    public static final String PEER_SURVEY_SYSTEM_GENERATED_NOT_ALLOWED = "SystemGenerated cannot be set to true";
    public static final String PEER_SURVEY_FIELD_TYPE_MISSING = "FieldType is mandatory";
    public static final String PEER_SURVEY_FIELD_TYPE_INVALID = "Only SCQ, MCQ and Text type questions are allowed";
    public static final String PEER_SURVEY_QUESTION_NAME_MISSING = "Question name cannot be blank";
    public static final String IDENTIFIER = "identifier";
    public static final String TRIGGER_AFTER = "triggerAfter";
    public static final String COMPLETION_LOOK_BACK  = "completionLookBack";
    public static final String THUMBNAIL = "thumbnail";
    public static final String DURATION = "duration";
    public static final String PEER_SURVEY_ONLY_LIVE_COURSE_ALLOWED = "Peer validation survey can only be created for LIVE courses/programs";
    public static final String PEER_SURVEY_INVALID_CONTENT_TYPE = "Selected content type is not allowed for peer validation survey";
    public static final String RESPONSE_DATA = "responseData";
    public static final String EXCEPTION = "Exception in %s : %s";
    public static final String PEER_SURVEY_ADDITIONAL_PROPERTIES_MISSING = "Additional properties are mandatory for peer validation survey";
    public static final String RADIO = "radio";
    public static final String CHECKBOX = "checkbox";
    public static final String TEXTAREA = "textarea";
    public static final String SYSTEM_GENERATED = "systemGenerated";
    public static final String USER = "user";
    public static final String FIELDS_CONSTANT = "fields";
    public static final String USER_ID = "userId";
    public static final String OK = "OK";
    public static final String RESPONSE_CODE = "responseCode";
    public static final String RESULT = "result";
    public static final String PROFILE_DETAILS  = "profileDetails";
    public static final String PERSONAL_DETAILS = "personalDetails";
    public static final String FIRSTNAME  = "FIRSTNAME";
    public static final String FIRST_NAME_LOWER_CASE  = "firstname";
    public static final String ASSIGNMENT_URL = "assignmentUrl";
    public static final String ERR_ADDITIONAL_PROPERTIES_MISSING = "Additional properties are required";
    public static final String ERR_BATCH_ID_MISSING = "BatchId is required";
    public static final String ERR_COURSE_ID_MISSING = "CourseId is required";
    public static final String ERR_ASSIGNMENT_URL_MISSING = "AssignmentUrl is required";
    public static final String INVALID_CONTEXT_TYPE = "Invalid contextType";
    public static final String ERR_DUPLICATE_ASSIGNMENT_BATCH = "A published assignment already exists for this batchId";
    public static final String CREATED_FIELDS_COUNT = "createdFieldsCount";
    public static final String UPDATED_FIELDS_COUNT = "updatedFieldsCount";
    public static final String DELETED_FIELDS_COUNT = "deletedFieldsCount";
    public static final String FIELD_ID = "fieldId";
    public static final String API_SEARCH_FORM    = "api.search.v3";
    public static final String SUBMISSION_COUNT   = "submissionCount";
    public static final String DESC               = "DESC";
    public static final String ID = "id";
    public static final String SUBMITTED_CAPS = "SUBMITTED";
    public static final String FULL_NAME = "fullName";
    public static final String DOCUMENT_ID = "documentId";
    public static final String FIRST_NAME = "firstName";
    public static final String ERR_FORM_ID_MISSING          = "formId is required";
    public static final String ERR_STATUS_MISSING           = "status is required";
    public static final String ERR_INVALID_STATUS           = "Invalid submission status. Allowed: DRAFT, SUBMITTED";
    public static final String ERR_RESPONSES_MISSING        = "responses are required for SUBMITTED status";
    public static final String ERR_INVALID_USER_SUBMISSION  = "Invalid user submission at index ";
    public static final String ERR_QUESTION_TEXT_MISSING    = "Question text missing at index ";
    public static final String ERR_QUESTION_ID_MISSING      = "Question ID missing at index ";
    public static final String ERR_ANSWER_TYPE_MISSING      = "Answer type missing for question: ";
    public static final String API_SUBMIT_FORM              = "api.saveFormSubmit.v3";
    public static final String DRAFT                        = "DRAFT";
    public static final String SUBMITTED                    = "SUBMITTED";
    public static final String RESPONSES_COUNT              = "responsesCount";
    public static final String SAVED_STATUS                 = "savedStatus";
    public static final String FORM = "form";
    public static final String X_AUTHENTICATED_USER_ID     = "x-authenticated-userid";
    public static final String API_GET_SAVED_FORM     = "api.getApplicationsById.v3";
    public static final String ERR_CONTEXT_ID_MISSING = "contextId is required";
    public static final String API_GET_ALL_APPLICATIONS     = "api.getAllApplications.v3";
    public static final String SUBMITTED_BY  = "submittedby";
    public static final String CONTEXT_ID    = "contextId";
    public static final String CONTEXT_NAME  = "contextName";
    public static final String API_SUBMISSION_SEARCH = "api.submission.search.v3";

    // ── Column names (PostgreSQL) ──────────────────────────────────
    public static final String COL_FORM_ID        = "formid";
    public static final String COL_SUBMITTED_BY   = "submittedby";
    public static final String COL_UPDATED_BY     = "updatedby";
    public static final String COL_CONTEXT_ID     = "contextid";
    public static final String COL_CONTEXT_TYPE   = "contexttype";
    public static final String COL_CONTEXT_ORG_ID = "contextorgid";
    public static final String COL_SUBMITTED_DATE = "submitteddate";
    public static final String COL_UPDATED_DATE   = "updateddate";

    // ── Entity field names (JPA) ───────────────────────────────────
    public static final String FIELD_FORM_ID        = "formId";
    public static final String FIELD_SUBMITTED_BY   = "submittedBy";
    public static final String FIELD_UPDATED_BY     = "updatedBy";
    public static final String FIELD_CONTEXT_ID     = "contextId";
    public static final String FIELD_CONTEXT_TYPE   = "contextType";
    public static final String FIELD_CONTEXT_ORG_ID = "contextOrgId";
    public static final String FIELD_SUBMITTED_DATE = "submittedDate";
    public static final String FIELD_UPDATED_DATE   = "updatedDate";

    public static final String FIELD_SUBMISSION_ID = "submissionId";
    public static final String FIELD_USER_ID       = "userId";
    public static final String RESPONSES           = "responses";
    public static final String SUBMISSION_META     = "submissionMeta";

    // ── Feedback ───────────────────────────────────────────────────
    public static final String API_FEEDBACK          = "api.feedback.v3";
    public static final String EVALUATED             = "EVALUATED";
    public static final String TOTAL_MARKS_GIVEN     = "totalMarksGiven";
    public static final String MAXIMUM_MARKS         = "maximumMarks";
    public static final String INSTRUCTOR_FEEDBACK   = "instructorFeedback";
    public static final String INSTRUCTOR_ID         = "instructorId";

    public static final String ERR_SUBMIT_URL_MISSING       = "submitUrl is required";
    public static final String API_PROCESS_ASSIGNMENT        = "api.processAssignmentAnswer.v3";

    private Constants() {}
}
