package com.karmayogi.form.model;

import lombok.Data;
import lombok.ToString;

/**
 * @author anil
 */
@Data
@ToString(includeFieldNames = true)
public class Meta {
    private String key;
    private String value;
}

