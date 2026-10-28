package com.karmayogi.form.validator;

import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.FieldMeta;
import com.karmayogi.form.model.Value;
import com.karmayogi.form.utils.Constants;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * @author anil
 */
@ExtendWith(MockitoExtension.class)
public class FormValidatorTest {

    @Mock
    private InputValidator inputValidator;

    @Mock
    private FormConfig formConfig;

    @InjectMocks
    private FormValidator formValidator;

    @BeforeEach
    void setUp() {
        lenient().when(formConfig.getAllowedFieldTypes())
                .thenReturn(Set.of("heading", "radio", "text"));
        lenient().when(formConfig.getAllowedFieldPropertyKeys())
                .thenReturn(Set.of("width"));
        lenient().when(inputValidator.validateFieldName(anyString())).thenReturn(null);
        lenient().when(inputValidator.validateText(any(), anyString()))
                .thenAnswer(inv -> StringUtils.isBlank(inv.getArgument(0))
                        ? inv.getArgument(1) + " is required" : null);
    }

    @Test
    void headingWithHeadingValuesIsValid() {
        FieldMeta heading = field("heading", 1, value(null, null, "Section A", "This is section A"));
        FieldMeta radio = field("radio", 2, value("Modi", "Modi", null, null));

        assertNull(formValidator.validateFields(List.of(heading, radio)));
    }

    @Test
    void choiceFieldValueWithoutKeyIsRejected() {
        FieldMeta radio = field("radio", 1, value(null, "Modi", null, null));

        assertEquals(Constants.ERR_VALUE_KEY_INVALID + null,
                formValidator.validateFields(List.of(radio)));
    }

    @Test
    void choiceFieldWithoutValuesIsRejected() {
        FieldMeta radio = field("radio", 1);
        radio.setValues(Collections.emptyList());

        assertEquals("Values are required for fieldType: radio",
                formValidator.validateFields(List.of(radio)));
    }

    private FieldMeta field(String fieldType, int order, Value... values) {
        FieldMeta field = new FieldMeta();
        field.setName(fieldType + " field");
        field.setFieldType(fieldType);
        field.setOrder(order);
        field.setValues(List.of(values));
        return field;
    }

    private Value value(String key, String text, String heading, String subHeading) {
        Value value = new Value();
        value.setKey(key);
        value.setValue(text);
        value.setHeading(heading);
        value.setSubHeading(subHeading);
        return value;
    }
}
