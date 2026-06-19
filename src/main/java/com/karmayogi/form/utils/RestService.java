package com.karmayogi.form.utils;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * @author anil
 */
@Service
@RequiredArgsConstructor
public class RestService {

    public static final Logger logger = LoggerFactory.getLogger(RestService.class);

    private final RestTemplate restTemplate;

    public Object postRequest(HttpHeaders headers, String url, Object request) {
        try {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Object> httpEntity = new HttpEntity<>(request, headers);
            logger.info("URI Being passed : " + url);
            logger.info("HTTP Entity Being passed : " + httpEntity);
            return restTemplate.postForObject(url, httpEntity, Map.class);

        } catch (Exception e) {
            logger.error(String.format(Constants.EXCEPTION, "getResponse", e.getMessage()));
        }
        return null;
    }

    public Object getRequestWithHeaders(HttpHeaders headers, String url) {
        try {
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Object> httpEntity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Map.class);
            if (response.getBody() != null && response.getBody().containsKey(Constants.RESPONSE_DATA)) {
                return response.getBody().get(Constants.RESPONSE_DATA);
            }
        } catch (Exception e) {
            logger.error(String.format(Constants.EXCEPTION, "getRequest", e.getMessage()));
        }
        return null;
    }

}
