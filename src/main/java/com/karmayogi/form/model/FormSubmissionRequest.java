package com.karmayogi.form.model;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * @author anil
 */
@Data
@Builder
@ToString(includeFieldNames = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
public class FormSubmissionRequest {

    private String formId;
    private String contextId;
    private String contextName;
    private String status;
    private Long timestamp;
    private Integer version;
    private List<QuestionResponse> responses;
    private Map<String, Object> submissionMeta;
    private String submitUrl;
    private String contextType;
    private String contextOrgId;
    private List<String> attachments;
    private List<String> peerIds;
    private String actionType;
    private String submissionId;
    private String reviewStatus;
    private String notificationId;
    private String createdAt;
    private String thumbnail;
    private String email;

}

