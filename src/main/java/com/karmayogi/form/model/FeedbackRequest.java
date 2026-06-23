package com.karmayogi.form.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author anil
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {
    private String formId;
    private String status;
    private Integer marksGiven;
    private Integer maximumMarks;
    private String instructorFeedback;
    private String contextId;
    private String instructorId;
    private String submittedBy;
}

