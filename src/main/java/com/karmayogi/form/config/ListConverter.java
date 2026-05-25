package com.karmayogi.form.config;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
/**
 * @author anil
 */
@Converter
public class ListConverter implements AttributeConverter<List<Object>,String> {
    private static final ObjectMapper M = new ObjectMapper();
    public String convertToDatabaseColumn(List<Object> a) {
        if(a==null) return null; try{return M.writeValueAsString(a);}catch(Exception e){return null;}
    }
    public List<Object> convertToEntityAttribute(String s) {
        if(s==null||s.isBlank()||"null".equals(s)) return null;
        try{return M.readValue(s,new TypeReference<>(){});}catch(Exception e){return null;}
    }
}
