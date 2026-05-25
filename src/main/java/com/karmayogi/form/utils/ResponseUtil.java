package com.karmayogi.form.utils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author anil
 */

public class ResponseUtil {

    public static Map<String, Object> buildResponse(String apiId, Object result) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "success");
        params.put("err", null);
        params.put("errMsg", null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id",           apiId);
        response.put("ver",          "v2");
        response.put("ts",           Instant.now().toString());
        response.put("params",       params);
        response.put("responseCode", "OK");
        response.put("result",       Map.of("response", result));

        return response;
    }
}
