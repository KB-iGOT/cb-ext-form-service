package com.karmayogi.form.validator;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.model.Meta;
import com.karmayogi.form.model.Value;
import com.karmayogi.form.utils.Constants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author anil
 */
public abstract class BaseFormValidator {

    protected final com.karmayogi.form.validator.InputValidator inputValidator;
    protected final com.karmayogi.form.utils.UserUtils utilityService;
    protected final FormConfig formConfig;

    protected BaseFormValidator(com.karmayogi.form.validator.InputValidator inputValidator,
                                com.karmayogi.form.utils.UserUtils utilityService, FormConfig formConfig) {
        this.inputValidator = inputValidator;
        this.utilityService = utilityService;
        this.formConfig = formConfig;
    }


    protected String validateTitleAndVersion(FormRequest request) {
        String titleError = validateTitle(request.getTitle());
        if (titleError != null) {
            return titleError;
        }
        if (request.getClientVersion() == null) {
            return Constants.ERR_CLIENT_VERSION_MISSING;
        }
        if (request.getClientVersion() <= 0.0f) {
            return Constants.ERR_INVALID_CLIENT_VERSION;
        }
        return null;
    }


    protected String validateTitle(String title) {
        return inputValidator.validateTitle(title);
    }


    protected String validateFieldName(String name) {
        return inputValidator.validateFieldName(name);
    }


    protected String validateText(String text, String fieldLabel) {
        return inputValidator.validateText(text, fieldLabel);
    }

    protected String validateValues(List<Value> values) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        for (Value value : values) {
            String keyError = validateText(value.getKey(), "Value key");
            if (keyError != null) {
                return Constants.ERR_VALUE_KEY_INVALID + value.getKey();
            }
            String textError = validateText(value.getValue(), "Value text");
            if (textError != null) {
                return Constants.ERR_VALUE_TEXT_INVALID + value.getValue();
            }
        }
        return null;
    }

    protected String validateAdditionalProperties(Map<String, Object> properties,
                                                  Set<String> allowedKeys) {
        if (MapUtils.isEmpty(properties)) {
            return null;
        }

        String keyError = inputValidator.validateAllowedKeys(properties, allowedKeys);
        if (keyError != null) {
            return keyError;
        }

        return inputValidator.validateMapValues(properties);
    }

    protected String validateFieldAdditionalProperties(List<FieldMeta> fields,
                                                       Set<String> allowedKeys) {
        if (CollectionUtils.isEmpty(fields)) {
            return null;
        }
        for (FieldMeta field : fields) {
            String error = validateAdditionalProperties(
                    field.getAdditionalProperties(), allowedKeys);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    protected String validateUser(String userId) throws IOException {
        if (org.apache.commons.lang3.StringUtils.isBlank(userId)) {
            return Constants.ERR_INVALID_USER_ID;
        }
        Map<String, Object> userData = utilityService.getUsersReadData(userId);
        if (MapUtils.isEmpty(userData)) {
            return Constants.ERR_INVALID_USER_ID;
        }
        return null;
    }

    public final String validate(FormRequest request, String userId) throws IOException {

        if (request == null) {
            return "Request is required";
        }

        String userError = validateUser(userId);
        if (userError != null) {
            return userError;
        }

        String sanitizeError = validateRequestContent(request);
        if (sanitizeError != null) {
            return sanitizeError;
        }

        String error = validateTitleAndVersion(request);
        if (error != null) {
            return error;
        }

        if (StringUtils.isBlank(request.getContextType()) || !formConfig.getAllowedContextTypes().contains(request.getContextType())) {
            return Constants.INVALID_CONTEXT_TYPE;
        }

        return validateContextSpecific(request, userId);
    }


    protected abstract String validateContextSpecific(FormRequest request, String userId);


    public abstract java.util.Set<String> supportedContextTypes();

    protected String validateRequestContent(FormRequest request) {

        String error = inputValidator.validateObject(
                request.getAdditionalProperties(),
                "form.additionalProperties");

        if (error != null) {
            return error;
        }

        if (CollectionUtils.isNotEmpty(request.getFields())) {

            for (FieldMeta field : request.getFields()) {
                error = validateTextFields(field);
                if (error != null) {
                    return error;
                }
            }
        }

        if (CollectionUtils.isNotEmpty(request.getMeta())) {

            for (Meta meta : request.getMeta()) {

                error = inputValidator.validateObject(
                        meta.getKey(),
                        "meta.key");

                if (error != null) {
                    return error;
                }

                error = inputValidator.validateObject(
                        meta.getValue(),
                        "meta.value");

                if (error != null) {
                    return error;
                }
            }
        }

        return null;
    }

    private String validateTextFields(FieldMeta field) {

        String error;
        error = inputValidator.validateObject(
                field.getRefApi(),
                "refApi");

        if (error != null) {
            return error;
        }

        error = inputValidator.validateObject(
                field.getLogicalGroupCode(),
                "logicalGroupCode");
        if (error != null) {
            return error;
        }

        error = inputValidator.validateObject(
                field.getParentId(),
                "parentId");
        if (error != null) {
            return error;
        }

        error = inputValidator.validateObject(
                field.getSectionId(),
                "sectionId");
        if (error != null) {
            return error;
        }

        error = inputValidator.validateObject(
                field.getAdditionalProperties(),
                "field.additionalProperties");
        if (error != null) {
            return error;
        }

        return null;
    }
}