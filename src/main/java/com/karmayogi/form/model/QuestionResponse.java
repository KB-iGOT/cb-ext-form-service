package com.karmayogi.form.model;

import lombok.*;

/**
 * @author anil
 */
@Data
@Builder
@ToString(includeFieldNames = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class QuestionResponse {
    private String question;     // Question text
    private Object answer;       // Answer (String or numeric)
    private String questionId;   // Optional question ID
    private String answerType;
}
