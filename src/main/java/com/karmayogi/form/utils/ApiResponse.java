package com.karmayogi.form.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author anil
 */
@Getter
@Setter
public class ApiResponse {

    private String    id;
    private String    ver;
    private String    ts;
    private Params    params;
    private HttpStatus responseCode;

    @Getter(lombok.AccessLevel.NONE)
    private Map<String, Object> response = new HashMap<>();

    public ApiResponse() {
        this.ver    = "v1";
        this.ts     = new Timestamp(System.currentTimeMillis()).toString();
        this.params = new Params(UUID.randomUUID().toString());
    }

    public ApiResponse(String id) {
        this();
        this.id = id;
    }

    public Map<String, Object> getResult() {
        return response;
    }

    public void put(String key, Object value) {
        response.put(key, value);
    }

    public Object get(String key) {
        return response.get(key);
    }


    @Getter
    @Setter
    public static class Params {

        private String resmsgid;
        private String msgid;
        private String status;
        private String errMsg;
        private String errid;

        public Params(String resmsgid) {
            this.resmsgid = resmsgid;
            this.msgid    = UUID.randomUUID().toString();
        }
    }
}
