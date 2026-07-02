package com.karmayogi.form.model;

import lombok.*;

import java.util.Date;

/**
 * @author anil
 */
@Data
@Builder
@ToString(includeFieldNames = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PeerReview {

    private String peerId;
    private String status;
    private Date reviewedAt;
    private String designation;
}

