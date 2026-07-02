package com.karmayogi.form.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author anil
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(includeFieldNames = true)
public class FormRequest {

    private String id;
    private String formId;
    private String title;
    private String status;
    private List<Meta> meta;
    private List<FieldMeta> mandatoryFields;
    private Float clientVersion;
    private List<FieldMeta> fields;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();
    private String contextType;
    private List<CreatedFor> createdFor;
    private String endDate;

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}

