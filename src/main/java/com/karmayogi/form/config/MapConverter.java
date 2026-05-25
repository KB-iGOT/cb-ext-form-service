package com.karmayogi.form.config;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
/**
 * @author anil
 */
@Converter
public class MapConverter implements AttributeConverter<Map<String,Object>,String> {
    private static final ObjectMapper M = new ObjectMapper();
    public String convertToDatabaseColumn(Map<String,Object> a) {
        if(a==null) return null; try{return M.writeValueAsString(a);}catch(Exception e){return null;}
    }
    public Map<String,Object> convertToEntityAttribute(String s) {
        if(s==null||s.isBlank()||"null".equals(s)) return null;
        try{return M.readValue(s,new TypeReference<>(){});}catch(Exception e){return null;}
    }
}
