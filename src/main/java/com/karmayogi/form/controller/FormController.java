package com.karmayogi.form.controller;

import com.karmayogi.form.service.FormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

import static com.karmayogi.form.utils.ResponseUtil.buildResponse;

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

}
