package com.karmayogi.form.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import java.util.Map;


/**
 * @author anil
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "forms")
public class Forms {

    @Id
    @Column(name = "formid")
    private String formId;

    @Column(name = "title")
    private String title;

    @Column(name = "status")
    private String status;

    @Column(name = "contexttype")
    private String contextType;

    @Column(name = "version")
    private Integer version;

    @Column(name = "clientversion")
    private Double clientVersion;

    @Column(name = "startdate")
    private Long startDate;

    @Column(name = "enddate")
    private Long endDate;

    @Column(name = "archiveddate")
    private Long archivedDate;

    @Column(name = "createdat")
    private Long createdAt;

    @Column(name = "updatedat")
    private Long updatedAt;

    @Column(name = "createdby")
    private String createdBy;

    @Column(name = "updatedby")
    private String updatedBy;

    @Column(name = "orgid")
    private String orgId;

    @Column(name = "orgname")
    private String orgName;

    @Column(name = "batchid")
    private String batchId;

    @Column(name = "courseid")
    private String courseId;

    @Type(JsonBinaryType.class)
    @Column(name = "questions", columnDefinition = "jsonb")
    private Object questions;

    @Type(JsonBinaryType.class)
    @Column(name = "additionalproperties", columnDefinition = "jsonb")
    private Map<String, Object> additionalProperties;

    @Type(JsonBinaryType.class)
    @Column(name = "mandatoryfields", columnDefinition = "jsonb")
    private Object mandatoryFields;

    @Type(JsonBinaryType.class)
    @Column(name = "meta", columnDefinition = "jsonb")
    private Object meta;
}