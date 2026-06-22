package com.karmayogi.form.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * @author anil
 */
@Configuration
@ConfigurationProperties(prefix = "form")
@Getter
@Setter
public class FormConfig {
    private Set<String> allowedSort;
    private Set<String> allowedFacets;
    private int         maxFields;
    private Set<String> allowedContextTypes;
    private Set<String> allowedFieldTypes;
    private Map<String, String> dateFromMap = new HashMap<>();
    private Map<String, String> dateToMap   = new HashMap<>();
    private Map<String, String> filterFieldMap =  new HashMap<>();
    private Double v2ClientVersion;
    private Set<String> allowedFieldPropertyKeys;
    private Set<String> allowedFormPropertyKeys;
    private List<String> peerSurveyContentReadFields;
    private String contentBaseUrl;
    private Set<String> peerSurveyAllowedAdditionalProperties;
    private Set<String> peerSurveyAllowedContentTypes;
    private int peerSurveyMinTriggerAfter;
    private int peerSurveyMaxTriggerAfter;
    private int peerSurveyTriggerAfterStep;
    private int peerSurveyMinLookback;
    private int peerSurveyMaxLookback;
    private int peerSurveyMaxFields;
    private String allowedUrlRegex;
    private String sbUrl;
    private String userSearchEndPoint;
    private Double v1ClientVersion;
    private Set<String> nonQuestionFieldTypes;
    private int searchMaxPage;
    private int searchMaxPageSize;
    private int searchDefaultPageSize;
    private int esTimeoutSeconds;
    private String searchIndex;
    private List<String> searchSupportedContextTypes;
    private List<String> searchAllowedFilterKeys;
    private java.util.List<String> submissionAllowedFilterKeys;
    private java.util.List<String> formContextHideCreatorFields;
    private int maxAllowedFilterListSize;

}
