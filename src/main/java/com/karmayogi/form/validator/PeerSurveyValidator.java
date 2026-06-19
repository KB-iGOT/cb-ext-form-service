package com.karmayogi.form.validator;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.ContentUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author anil
 *
 */
@Component
public class PeerSurveyValidator extends BaseFormValidator {

    private final FormConfig formConfig;
    private final ContentUtils contentUtils;

    public PeerSurveyValidator(InputValidator inputValidator,
                               com.karmayogi.form.utils.UserUtils utilityService,
                               FormConfig formConfig,
                               ContentUtils contentUtils) {
        super(inputValidator, utilityService, formConfig);
        this.formConfig = formConfig;
        this.contentUtils = contentUtils;
    }

    @Override
    public java.util.Set<String> supportedContextTypes() {
        return java.util.Set.of(Constants.PEER_VALIDATION_SURVEY);
    }


    @Override
    protected String validateContextSpecific(FormRequest request, String userId) {

        Map<String, Object> additionalProps = request.getAdditionalProperties();
        if (MapUtils.isEmpty(additionalProps)) {
            return Constants.PEER_SURVEY_ADDITIONAL_PROPERTIES_MISSING;
        }

        // 3. Shared algorithm, peer-survey's OWN narrower allow-list (Layer 2)
        String propsError = validateAdditionalProperties(
                additionalProps,
                formConfig.getPeerSurveyAllowedAdditionalProperties());
        if (propsError != null) {
            return propsError;
        }

        // 4. identifier required → content-service lookup
        Object identifier = additionalProps.get(Constants.IDENTIFIER);
        if (identifier == null) {
            return Constants.PEER_SURVEY_ADDITIONAL_PROPERTIES_MISSING;
        }

        Map<String, Object> content = contentUtils.getContentFromCache((String) identifier);

        String contentType = (String) content.get("courseCategory");
        if (!formConfig.getPeerSurveyAllowedContentTypes().contains(contentType)) {
            return Constants.PEER_SURVEY_INVALID_CONTENT_TYPE;
        }

        String status = (String) content.get("status");
        if (!"Live".equalsIgnoreCase(status)) {
            return Constants.PEER_SURVEY_ONLY_LIVE_COURSE_ALLOWED;
        }

        // 5. triggerAfter required, within configured range, multiple of configured step
        Object triggerObj = additionalProps.get(Constants.TRIGGER_AFTER);
        if (triggerObj == null) {
            return Constants.PEER_SURVEY_ADDITIONAL_PROPERTIES_MISSING;
        }

        int triggerAfter;
        try {
            triggerAfter = Integer.parseInt(triggerObj.toString());
        } catch (Exception e) {
            return Constants.PEER_SURVEY_TRIGGER_AFTER_INVALID;
        }

        if (triggerAfter < formConfig.getPeerSurveyMinTriggerAfter()
                || triggerAfter > formConfig.getPeerSurveyMaxTriggerAfter()
                || triggerAfter % formConfig.getPeerSurveyTriggerAfterStep() != 0) {
            return Constants.PEER_SURVEY_TRIGGER_AFTER_RANGE_INVALID;
        }

        // 6. completionLookBack optional, within configured range, must exceed triggerAfter
        Object lookBackObj = additionalProps.get(Constants.COMPLETION_LOOK_BACK);
        if (lookBackObj != null) {
            int lookBack;
            try {
                lookBack = Integer.parseInt(lookBackObj.toString());
            } catch (Exception e) {
                return Constants.PEER_SURVEY_LOOKBACK_INVALID;
            }

            if (lookBack < formConfig.getPeerSurveyMinLookback()
                    || lookBack > formConfig.getPeerSurveyMaxLookback()) {
                return Constants.PEER_SURVEY_LOOKBACK_INVALID;
            }
            if (lookBack <= triggerAfter) {
                return Constants.PEER_SURVEY_LOOKBACK_LESS_THAN_TRIGGER;
            }
        }

        // 7. thumbnail required, must be a valid URL (intentional
        // exception to the usual "reject URLs" rule)
        String thumbnail = (String) additionalProps.get(Constants.THUMBNAIL);
        if (StringUtils.isBlank(thumbnail)) {
            return Constants.PEER_SURVEY_THUMBNAIL_MISSING;
        }
        if (!thumbnail.matches(formConfig.getAllowedUrlRegex())) {
            return Constants.PEER_SURVEY_THUMBNAIL_INVALID;
        }

        // 8. duration required (presence only)
        String duration = (String) additionalProps.get(Constants.DURATION);
        if (StringUtils.isBlank(duration)) {
            return Constants.PEER_SURVEY_DURATION_MISSING;
        }

        // 9. Optional extra questions (capped, narrow fieldType set)
        return validatePeerSurveyFields(request.getFields());
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATE FIELDS — peer-survey's own narrow rules
    // ─────────────────────────────────────────────────────────────

    private String validatePeerSurveyFields(List<FieldMeta> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return null; // extra questions are optional
        }

        if (fields.size() > formConfig.getPeerSurveyMaxFields()) {
            return Constants.PEER_SURVEY_MAX_ADDITIONAL_QUESTIONS;
        }
        List<Integer> fieldOrders = new ArrayList<>();
        for (FieldMeta field : fields) {

            // systemGenerated explicitly forbidden for peer-survey questions
            if (MapUtils.isNotEmpty(field.getAdditionalProperties())) {
                Object systemGen = field.getAdditionalProperties()
                        .get(Constants.SYSTEM_GENERATED);
                if (Boolean.TRUE.equals(systemGen)) {
                    return Constants.PEER_SURVEY_SYSTEM_GENERATED_NOT_ALLOWED;
                }
            }

            if (StringUtils.isBlank(field.getFieldType())) {
                return Constants.PEER_SURVEY_FIELD_TYPE_MISSING;
            }

            String fieldType = field.getFieldType().toLowerCase();
            if (!fieldType.equals(Constants.RADIO)
                    && !fieldType.equals(Constants.CHECKBOX)
                    && !fieldType.equals(Constants.TEXTAREA)) {
                return Constants.PEER_SURVEY_FIELD_TYPE_INVALID;
            }

            // Original behavior: blank check
            if (StringUtils.isBlank(field.getName())) {
                return Constants.PEER_SURVEY_QUESTION_NAME_MISSING;
            }

            if (field.getOrder() == null) {
                return Constants.ERR_FIELD_ORDER_MISSING;
            }
            if (field.getOrder() <= 0 || fieldOrders.contains(field.getOrder())) {
                return Constants.ERR_INVALID_FIELD_ORDER;
            }
            fieldOrders.add(field.getOrder());
            // Added: Shared field name safety check (Layer 2) —
            // see class-level note on this behavior change
            String nameError = validateFieldName(field.getName());
            if (nameError != null) {
                return nameError;
            }
        }

        return null; // valid ✅
    }
}