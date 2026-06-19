package com.karmayogi.form.validator;

import com.karmayogi.form.utils.Constants;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author anil
 */
@Component
public class InputValidator {

    @Value("${form.validation.url-pattern}")
    private String urlPatternStr;

    @Value("${form.validation.html-pattern:<[^>]*>}")
    private String htmlPatternStr;

    @Value("${form.validation.script-pattern:(?i)(script|javascript|onerror|onload|onclick|eval\\(|alert\\()}")
    private String scriptPatternStr;

    @Value("${form.validation.max-title-length:255}")
    private int maxTitleLength;

    @Value("${form.validation.max-field-name-length:500}")
    private int maxFieldNameLength;

    private Pattern urlPattern;
    private Pattern htmlPattern;
    private Pattern scriptPattern;

    @PostConstruct
    public void init() {
        urlPattern    = Pattern.compile(urlPatternStr, Pattern.CASE_INSENSITIVE);
        htmlPattern   = Pattern.compile(htmlPatternStr, Pattern.CASE_INSENSITIVE);
        scriptPattern = Pattern.compile(scriptPatternStr, Pattern.CASE_INSENSITIVE);
    }

    public String validateTitle(String title) {
        if (StringUtils.isBlank(title)) {
            return Constants.ERR_TITLE_MISSING;
        }
        if (title.length() > maxTitleLength) {
            return Constants.ERR_TITLE_TOO_LONG;
        }
        if (containsUrl(title)) {
            return Constants.ERR_TITLE_CONTAINS_URL;
        }
        if (containsHtml(title) || containsScript(title)) {
            return Constants.ERR_INVALID_INPUT;
        }
        return null;
    }

    public String validateFieldName(String name) {
        if (StringUtils.isBlank(name)) {
            return Constants.ERR_FIELD_NAME_MISSING;
        }
        if (name.length() > maxFieldNameLength) {
            return Constants.ERR_FIELD_NAME_TOO_LONG;
        }
        if (containsUrl(name)) {
            return Constants.ERR_FIELD_NAME_CONTAINS_URL;
        }
        if (containsHtml(name) || containsScript(name)) {
            return Constants.ERR_INVALID_INPUT;
        }
        return null;
    }


    public String validateText(String text, String fieldName) {
        if (StringUtils.isBlank(text)) {
            return fieldName + " is required";
        }
        if (containsUrl(text)) {
            return fieldName + " should not contain URLs";
        }
        if (containsHtml(text) || containsScript(text)) {
            return fieldName + " contains invalid content";
        }
        return null;
    }

    public boolean containsUrl(String text) {
        return urlPattern.matcher(text).find();
    }

    public boolean containsHtml(String text) {
        return htmlPattern.matcher(text).find();
    }

    public boolean containsScript(String text) {
        return scriptPattern.matcher(text).find();
    }


    public String validateAllowedKeys(Map<String, Object> map, Set<String> allowedKeys) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (String key : map.keySet()) {
            if (!allowedKeys.contains(key)) {
                return Constants.ERR_UNKNOWN_PROPERTY_KEY + key;
            }
        }
        return null;
    }
   
    public String validateMapValues(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String error = validateValueRecursive(entry.getKey(), entry.getValue());
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String validateValueRecursive(String fieldLabel, Object value) {
        if (value instanceof String) {
            String text = (String) value;
            if (containsUrl(text)) {
                return fieldLabel + " should not contain URLs";
            }
            if (containsHtml(text) || containsScript(text)) {
                return fieldLabel + " contains invalid content";
            }
        } else if (value instanceof Map) {
            String error = validateMapValues((Map<String, Object>) value);
            if (error != null) {
                return error;
            }
        } else if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                String error = validateValueRecursive(fieldLabel, item);
                if (error != null) {
                    return error;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public String validateObject(Object value, String fieldLabel) {

        if (value == null) {
            return null;
        }

        if (value instanceof String text) {

            if (containsUrl(text)) {
                return fieldLabel + " should not contain URLs";
            }

            if (containsHtml(text) || containsScript(text)) {
                return fieldLabel + " contains invalid content";
            }

            return null;
        }

        if (value instanceof Map<?, ?> map) {

            for (Map.Entry<?, ?> entry : map.entrySet()) {

                String key = String.valueOf(entry.getKey());

                String error =
                        validateObject(entry.getValue(), fieldLabel + "." + key);

                if (error != null) {
                    return error;
                }
            }

            return null;
        }

        if (value instanceof Iterable<?> iterable) {

            int index = 0;

            for (Object item : iterable) {

                String error =
                        validateObject(item, fieldLabel + "[" + index + "]");

                if (error != null) {
                    return error;
                }

                index++;
            }

            return null;
        }

        return null;
    }

}