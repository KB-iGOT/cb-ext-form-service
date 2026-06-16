package com.karmayogi.form.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Map;

/**
 * @author anil
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "form_questions")
public class FormQuestions {

    @Id
    @Column(name = "questionid")
    private String questionId;

    @Column(name = "formid", nullable = false)
    private String formId;

    @Column(name = "name")
    private String name;

    @Column(name = "fieldtype")
    private String fieldType;

    @Column(name = "questionorder")
    private Integer questionOrder;

    @Column(name = "isrequired")
    private Boolean isRequired;

    @Column(name = "notapplicable")
    private Boolean notApplicable;

    @Column(name = "status")
    private String status;

    @Column(name = "sectionid")
    private String sectionId;

    @Column(name = "parentid")
    private String parentId;

    @Column(name = "hidden")
    private Boolean hidden;

    @Column(name = "logicalgroupcode")
    private String logicalGroupCode;

    @Column(name = "refapi")
    private String refApi;

    @Type(JsonBinaryType.class)
    @Column(name = "values", columnDefinition = "jsonb")
    private List<Map<String, Object>> values;

    @Type(JsonBinaryType.class)
    @Column(name = "additionalproperties", columnDefinition = "jsonb")
    private Map<String, Object> additionalProperties;
}
