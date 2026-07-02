package com.karmayogi.form.validator;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.utils.Constants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author anil
 *
 */
@Component
public class FormValidator extends BaseFormValidator {

    public FormValidator(InputValidator inputValidator,
                         com.karmayogi.form.utils.UserUtils utilityService,
                         FormConfig formConfig) {
        super(inputValidator, utilityService, formConfig);
    }

    @Override
    public java.util.Set<String> supportedContextTypes() {
        java.util.Set<String> types =
                new java.util.HashSet<>(formConfig.getAllowedContextTypes());
        types.remove(Constants.PEER_VALIDATION_SURVEY);
        types.remove(Constants.ASSIGNMENT);
        return types;
    }

    @Override
    protected String validateContextSpecific(FormRequest request, String userId) {

        if (StringUtils.isBlank(request.getContextType())
                || !formConfig.getAllowedContextTypes()
                .contains(request.getContextType())) {
            return Constants.ERR_INVALID_CONTEXT_TYPE;
        }

        String formPropsError = validateAdditionalProperties(
                request.getAdditionalProperties(),
                formConfig.getAllowedFormPropertyKeys());
        if (formPropsError != null) {
            return formPropsError;
        }


        if (CollectionUtils.isEmpty(request.getFields())) {
            return Constants.ERR_FIELD_MISSING;
        }

        long questionCount = request.getFields().stream()
                .filter(f -> !formConfig.getNonQuestionFieldTypes().contains(f.getFieldType()))
                .count();
        if (questionCount > formConfig.getMaxFields()) {
            return "Maximum allowed fields size is " + formConfig.getMaxFields();
        }

        return validateFields(request.getFields());
    }

    public String validateFields(List<FieldMeta> fields) {

        List<Integer> fieldOrders = new ArrayList<>();

        for (FieldMeta field : fields) {

            if (StringUtils.isBlank(field.getFieldType())) {
                return Constants.ERR_FIELD_TYPE_MISSING;
            }
            if (!formConfig.getAllowedFieldTypes().contains(field.getFieldType())) {
                return Constants.ERR_INVALID_FIELD_TYPE;
            }


            String nameError = validateFieldName(field.getName());
            if (nameError != null) {
                return nameError;
            }


            if (field.getOrder() == null) {
                return Constants.ERR_FIELD_ORDER_MISSING;
            }
            if (field.getOrder() <= 0 || fieldOrders.contains(field.getOrder())) {
                return Constants.ERR_INVALID_FIELD_ORDER;
            }
            fieldOrders.add(field.getOrder());


            if (isChoiceField(field.getFieldType())
                    && CollectionUtils.isEmpty(field.getValues())) {
                return "Values are required for fieldType: " + field.getFieldType();
            }


            String valuesError = validateValues(field.getValues());
            if (valuesError != null) {
                return valuesError;
            }


            String fieldPropsError = validateAdditionalProperties(
                    field.getAdditionalProperties(),
                    formConfig.getAllowedFieldPropertyKeys());
            if (fieldPropsError != null) {
                return fieldPropsError;
            }
        }

        return null;
    }


    private boolean isChoiceField(String fieldType) {
        return "dropdown".equals(fieldType)
                || "checkbox".equals(fieldType)
                || "radio".equals(fieldType)
                || "numericRating".equals(fieldType);
    }
}