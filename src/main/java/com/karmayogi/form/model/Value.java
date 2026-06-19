package com.karmayogi.form.model;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * @author anil
 */
@Data
@ToString(includeFieldNames = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "heading", "subHeading", "key", "value" })
public class Value {

    @JsonProperty("heading")
    private String heading;

    @JsonProperty("subHeading")
    private String subHeading;

    @JsonProperty("key")
    private String key;

    @JsonProperty("value")
    private String value;

    @JsonIgnore
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

