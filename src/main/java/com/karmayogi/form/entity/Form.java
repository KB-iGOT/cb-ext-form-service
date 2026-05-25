package com.karmayogi.form.entity;

import com.karmayogi.form.config.ListConverter;
import com.karmayogi.form.config.MapConverter;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
/**
 * @author anil
 */
@Entity
@Table(name = "forms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Form {

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
    private BigDecimal version;

    @Column(name = "clientversion")
    private String clientVersion;

    @Column(name = "startdate")
    private Instant startDate;

    @Column(name = "enddate")
    private Instant endDate;

    @Column(name = "createdat")
    private Instant createdAt;

    @Column(name = "updatedat")
    private Instant updatedAt;

    @Column(name = "archiveddate")
    private Instant archivedDate;

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

    @Column(name = "additionalproperties", columnDefinition = "jsonb")
    @Convert(converter = MapConverter.class)
    private Map<String, Object> additionalProperties;

    @Column(name = "mandatoryfields", columnDefinition = "jsonb")
    @Convert(converter = ListConverter.class)
    private List<Object> mandatoryFields;

    @Column(name = "meta", columnDefinition = "jsonb")
    @Convert(converter = MapConverter.class)
    private Map<String, Object> meta;
}

