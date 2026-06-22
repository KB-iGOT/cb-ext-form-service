package com.karmayogi.form.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SearchResultData(List<Map<String, Object>> forms,
                               Set<String> formIds) {
}
