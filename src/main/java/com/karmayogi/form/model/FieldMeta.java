package com.karmayogi.form.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author anil
 */
@Data
@Getter
@Setter
public class FieldMeta {

    private String id;
    private String formId;
    private String contextType;
    private String refApi;
    private String logicalGroupCode;
    private String name;
    private String fieldType;
    private List<Value> values;
    private Integer order;
    private Boolean isRequired;
    private Boolean notApplicable;
    private String sectionId;
    private String parentId;
    private Boolean hidden;
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }
    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}

