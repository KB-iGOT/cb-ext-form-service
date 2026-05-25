package com.karmayogi.form.controller;

import com.karmayogi.form.service.FormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * @author anil
 */
@RestController
@RequestMapping("/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchForms(
            @RequestBody Map<String, Object> body) {
        Map<String, Object> request = (Map<String, Object>) body.getOrDefault("request", body);

        Map<String, Object> searchResult = formService.searchForms(request);

        return ResponseEntity.ok(buildResponse("api.forms.search", searchResult));
    }

    private Map<String, Object> buildResponse(String apiId, Object result) {
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
