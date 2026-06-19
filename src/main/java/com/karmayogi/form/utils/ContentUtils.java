package com.karmayogi.form.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.FormConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class ContentUtils {

    private static final Logger logger = LoggerFactory.getLogger(ContentUtils.class);

    private final FormConfig appConfiguration;

    private final FormCacheService cacheService;

    private final RestService restService;

    public Map<String, Object> getContentFromCache(String id) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String cacheResponse = cacheService.getCache(id);
            if (StringUtils.isNotBlank(cacheResponse)) {
                try {
                    return mapper.readValue(cacheResponse, new TypeReference<Map<String, Object>>() {
                    });
                } catch (IOException e) {
                    logger.warn("Failed to parse cached data for userId {}", id, e);
                }
            }
        } catch (Exception e) {
            logger.error("ContentCacheHandlerV2:getContent: Error while reading content from Redis for id: " + id, e);
        }
        Map<String, Object> content = getContent(id);

        if (MapUtils.isNotEmpty(content)) {
            try {
                cacheService.putCache(
                        id,
                        mapper.writeValueAsString(content),
                        60,
                        java.util.concurrent.TimeUnit.MINUTES);
            } catch (Exception e) {
                logger.warn("Failed to cache content {}", id, e);
            }
        }
        return content;
    }

    public Map<String, Object> getContent(String courseId) {
        Map<String, Object> resMap = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        String fields = String.join(",", appConfiguration.getPeerSurveyContentReadFields());

        String url = appConfiguration.getContentBaseUrl()
                + "/content/v3/read/" + courseId
                + "?fields=" + fields;
        try {

            logger.info("making call for content read ==" + courseId);
            String response = restService.getRequestWithHeaders(new HttpHeaders(), url).toString();

            Map<String, Object> data = mapper.readValue(response, Map.class);
            if (MapUtils.isNotEmpty(data)) {
                data = (Map<String, Object>) data.get(Constants.RESULT);
                if (MapUtils.isNotEmpty(data)) {
                    Object content = data.get(Constants.CONTENT);
                    resMap.put(Constants.CONTENT, content);
                } else {
                    logger.info( "EkStepRequestUtil:searchContent No data found");
                }
            } else {
                logger.info("EkStepRequestUtil:searchContent No data found");
            }
        } catch (IOException e) {
            logger.error("Error found during content search parse==" + e.getMessage(), e);
        }
        return resMap;
    }
}

