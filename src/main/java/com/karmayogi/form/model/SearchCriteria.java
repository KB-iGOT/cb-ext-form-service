package com.karmayogi.form.model;

/**
 * @author anil
 */

import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Data
@ToString(includeFieldNames = true)
public class SearchCriteria {
    private String query;
    private Map<String, Object> filters;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortOrder;
    private String groupBy;
    private List<String> fields;
    private String contextType;
}
