package com.karmayogi.form.validator;


import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.FormRequest;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.UserUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * @author anil
 *
 */
@Component
public class AssignmentValidator extends BaseFormValidator {

    public AssignmentValidator(InputValidator inputValidator,
                               UserUtils utilityService,
                               FormConfig formConfig) {
        super(inputValidator, utilityService, formConfig);
    }


    @Override
    public Set<String> supportedContextTypes() {
        return Set.of(Constants.ASSIGNMENT);
    }


    @Override
    protected String validateContextSpecific(FormRequest request, String userId) {

        Map<String, Object> props = request.getAdditionalProperties();
        if (MapUtils.isEmpty(props)) {
            return Constants.ERR_ADDITIONAL_PROPERTIES_MISSING;
        }

        if (props.get(Constants.BATCH_ID) == null
                || StringUtils.isBlank(props.get(Constants.BATCH_ID).toString())) {
            return Constants.ERR_BATCH_ID_MISSING;
        }

        if (props.get(Constants.COURSE_ID) == null
                || StringUtils.isBlank(props.get(Constants.COURSE_ID).toString())) {
            return Constants.ERR_COURSE_ID_MISSING;
        }

        if (props.get(Constants.ASSIGNMENT_URL) == null
                || StringUtils.isBlank(props.get(Constants.ASSIGNMENT_URL).toString())) {
            return Constants.ERR_ASSIGNMENT_URL_MISSING;
        }
        return null;
    }
}
