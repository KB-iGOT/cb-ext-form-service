package com.karmayogi.form.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karmayogi.form.config.FormConfig;
import com.karmayogi.form.model.SunbirdApiRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author anil
 */
@Component
@RequiredArgsConstructor
public class UserUtils {

    private static final Logger log = LoggerFactory.getLogger(UserUtils.class);

    private final FormCacheService formCacheService;
    private final ObjectMapper mapper;
    private final FormConfig formConfig;
    private final RestService restService;

    public Map<String, Object> getUsersReadData(String userId) throws IOException {
        String cacheKey = Constants.USER + ":form:" + userId;
        String cachedJson = formCacheService.getCache(cacheKey);
        if (StringUtils.isNotBlank(cachedJson)) {
            try {
                return mapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException e) {
                log.warn("Failed to parse cached data for userId {}", userId, e);
            }
        }
        Map<String, Map<String, String>> users =
                getUserDetails(Collections.singletonList(userId), Arrays.asList(Constants.PROFILE_DETAILS));

        if (MapUtils.isEmpty(users)) {
            return Collections.emptyMap();
        }

        Map<String, String> userData = new HashMap<>(users.getOrDefault(
                userId,
                Collections.emptyMap()));

        if (!userData.isEmpty()) {
            formCacheService.putCache(
                    cacheKey,
                    mapper.writeValueAsString(userData),
                    30,
                    TimeUnit.MINUTES);
        }

        return new HashMap<>(userData);

    }


    @SuppressWarnings("unchecked")
    public Map<String, Map<String, String>> getUserDetails(List<String> userIds,
                                                           List<String> fields) {

        SunbirdApiRequest requestObj = new SunbirdApiRequest();

        Map<String, Object> reqMap = new HashMap<>();

        reqMap.put(Constants.FILTERS, new HashMap<String, Object>() {{
            put(Constants.USER_ID, userIds);
        }});

        reqMap.put(Constants.FIELDS_CONSTANT, fields);

        requestObj.setRequest(reqMap);

        try {
            String url = formConfig.getSbUrl() + formConfig.getUserSearchEndPoint();

            HttpHeaders headers = new HttpHeaders();

            Map<String, Object> apiResponse =
                    (Map<String, Object>) restService.postRequest(
                            headers,
                            url,
                            requestObj);

            if (apiResponse == null) {
                return MapUtils.EMPTY_SORTED_MAP;
            }

            if (Constants.OK.equalsIgnoreCase(
                    (String) apiResponse.get(Constants.RESPONSE_CODE))) {

                Map<String, Object> result =
                        (Map<String, Object>) apiResponse.get(Constants.RESULT);

                Map<String, Object> searchResponse =
                        (Map<String, Object>) result.get(Constants.RESPONSE);

                int count =
                        ((Number) searchResponse.get(Constants.COUNT))
                                .intValue();

                if (count > 0) {

                    Map<String, Map<String, String>> userDetailsMap =
                            new HashMap<>();

                    List<Map<String, Object>> userProfiles =
                            (List<Map<String, Object>>) searchResponse.get(Constants.CONTENT);

                    if (CollectionUtils.isNotEmpty(userProfiles)) {

                        for (Map<String, Object> userProfile : userProfiles) {

                            Map<String, Object> profileDetails =
                                    (Map<String, Object>) userProfile.get(
                                            Constants.PROFILE_DETAILS);

                            if (profileDetails == null) {
                                continue;
                            }

                            Map<String, Object> personalDetails =
                                    (Map<String, Object>) profileDetails.get(
                                            Constants.PERSONAL_DETAILS);

                            if (personalDetails == null) {
                                continue;
                            }

                            Map<String, String> record = new HashMap<>();

                            record.put(
                                    Constants.USER_ID,
                                    userIds.get(0));

                            record.put(
                                    Constants.FIRSTNAME,
                                    (String) personalDetails.get(
                                            Constants.FIRST_NAME_LOWER_CASE));

                            userDetailsMap.put(
                                    record.get(Constants.USER_ID),
                                    record);
                        }
                    }

                    return userDetailsMap;
                }
            }

        } catch (Exception e) {
            log.error("Failed to get user details", e);
        }

        return MapUtils.EMPTY_SORTED_MAP;
    }

}

