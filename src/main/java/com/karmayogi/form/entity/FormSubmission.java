package com.karmayogi.form.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

/**
 * @author anil
 */
@Data
@Entity
@Table(name = "form_submissions")
public class FormSubmission {

    @Id
    @Column(name = "submissionid")
    private String submissionId;

    @Column(name = "formid")
    private String formId;

    @Column(name = "userid")
    private String userId;

    @Column(name = "contextid")
    private String contextId;

    @Column(name = "status")
    private String status;

    @Column(name = "fullname")
    private String fullName;

    @Column(name = "submittedby")
    private String submittedBy;

    @Column(name = "submitteddate")
    private Long submittedDate;

    @Column(name = "updatedby")
    private String updatedBy;

    @Column(name = "updateddate")
    private Long updatedDate;

    @Column(name = "version")
    private Integer version;

    @Column(name = "contexttype")
    private String contextType;

    @Column(name = "contextorgid")
    private String contextOrgId;

    @Column(name = "contextname")
    private String contextName;

    @Column(name = "submiturl")
    private String submitUrl;

    @Type(JsonBinaryType.class)
    @Column(name = "responses", columnDefinition = "jsonb")
    private List<Map<String, Object>> responses;

    @Type(JsonBinaryType.class)
    @Column(name = "attachments", columnDefinition = "jsonb")
    private List<String> attachments;

    @Type(JsonBinaryType.class)
    @Column(name = "peerreviews", columnDefinition = "jsonb")
    private Object peerReviews;

    @Type(JsonBinaryType.class)
    @Column(name = "submissionmeta", columnDefinition = "jsonb")
    private Map<String, Object> submissionMeta;
}
