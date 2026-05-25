package com.karmayogi.form.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
/**
 * @author anil
 */
@Configuration
@ConfigurationProperties(prefix = "form")
@Getter
@Setter
public class FormConfig {
    private Set<String> allowedSort;
    private Set<String> allowedFacets;
    private Map<String, String> dateFromMap = new HashMap<>();
    private Map<String, String> dateToMap   = new HashMap<>();
    private Map<String, String> filterFieldMap =  new HashMap<>();
}
