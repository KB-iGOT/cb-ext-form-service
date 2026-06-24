package com.karmayogi.form.config.cassandrautils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.exception.CustomException;
import com.karmayogi.form.exception.ProjectResponseCode;
import com.karmayogi.form.utils.ApiResponse;
import com.karmayogi.form.utils.Constants;
import com.karmayogi.form.utils.PropertiesCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
/**
 * @author anil
 */
@Component
@Slf4j
public class ProjectUtil {
    public static PropertiesCache propertiesCache;

    static {
        propertiesCache = PropertiesCache.getInstance();
    }

    @Autowired
    private ObjectMapper mapper;

    TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<List<Map<String, Object>>>() {
    };

    TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    /**
     * This method will create and return server exception to caller.
     *
     * @param responseCode ResponseCode
     * @return ProjectCommonException
     */
    public static CustomException createServerError(ProjectResponseCode responseCode) {
        return new CustomException(responseCode.getErrorCode(), responseCode.getErrorMessage(),
                ProjectResponseCode.SERVER_ERROR.getResponseCode());
    }

    public static CustomException createClientException(ProjectResponseCode responseCode) {
        return new CustomException(responseCode.getErrorCode(), responseCode.getErrorMessage(),
                ProjectResponseCode.CLIENT_ERROR.getResponseCode());
    }

    public static ApiResponse createDefaultResponse(String api, String apiVersion) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(StringUtils.isEmpty(apiVersion) ? Constants.API_VERSION_1 : apiVersion);
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(java.time.LocalDateTime.now().toString());
        return response;
    }

    public static void errorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.setResponseCode(httpStatus);
        response.getParams().setErrMsg(errorMessage);
    }

    public List<Map<String, Object>> parseListOfMap(String json) throws IOException {
        return mapper.readValue(json, LIST_OF_MAP_TYPE);
    }

    public Map<String, Object> parseMap(String json) throws IOException {
        return mapper.readValue(json, MAP_TYPE);
    }

    public String convertToString(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (IOException e) {
            log.error("Error converting object to string: {}", e.getMessage(), e);
            return null;
        }
    }

    public static String getConfigValue(String key) {
        if (StringUtils.isNotBlank(System.getenv(key))) {
            return System.getenv(key);
        }
        return propertiesCache.readProperty(key);
    }

}
