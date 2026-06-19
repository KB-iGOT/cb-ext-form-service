package com.karmayogi.form.validator;


import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author anil
 *
 */
@Component
public class ValidatorRegistry {

    private final List<BaseFormValidator> validators;
    private final Map<String, BaseFormValidator> validatorsByContextType = new HashMap<>();

    public ValidatorRegistry(List<BaseFormValidator> validators) {
        this.validators = validators;
    }

    @PostConstruct
    public void buildRegistry() {
        for (BaseFormValidator validator : validators) {
            for (String contextType : validator.supportedContextTypes()) {
                validatorsByContextType.put(contextType, validator);
            }
        }
    }

    public BaseFormValidator getValidator(String contextType) {
        return validatorsByContextType.get(contextType);
    }
}