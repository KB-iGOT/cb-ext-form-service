package com.karmayogi.form.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.common.util.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author anil
 */
@Component
public class AccessTokenValidator {

    @Autowired
    KeyManager keyManager;
    private static Logger logger = LoggerFactory.getLogger(AccessTokenValidator.class.getName());
    private static ObjectMapper mapper = new ObjectMapper();
    private static PropertiesCache cache = PropertiesCache.getInstance();

    private Map<String, Object> validateToken(String token) throws Exception {
        try {
            String[] tokenElements = token.split("\\.");
            String header = tokenElements[0];
            String body = tokenElements[1];
            String signature = tokenElements[2];
            String payLoad = header + Constants.DOT_SEPARATOR + body;
            Map<Object, Object> headerData =
                    mapper.readValue(new String(decodeFromBase64(header)), Map.class);
            String keyId = headerData.get("kid").toString();
            boolean isValid =
                    CryptoUtil.verifyRSASign(
                            payLoad,
                            decodeFromBase64(signature),
                            keyManager.getPublicKey(keyId).getPublicKey(),
                            Constants.SHA_256_WITH_RSA);
            if (isValid) {
                Map<String, Object> tokenBody =
                        mapper.readValue(new String(decodeFromBase64(body)), Map.class);
                boolean isExp = isExpired((Integer) tokenBody.get("exp"));
                if (isExp) {
                    throw new Exception("Expired auth token is received.");
                }
                return tokenBody;
            } else {
                throw new Exception("Invalid auth token is received.");
            }
        } catch (Exception e) {
            logger.warn("Failed to validate the user token. Exception: ", e);
        }
        return Collections.EMPTY_MAP;
    }


    public Map<String, Object> verifyUserToken(String token) {
        String userId = Constants._UNAUTHORIZED;
        Map<String, Object> tokenData = new HashMap<>();
        try {
            Map<String, Object> payload = validateToken(token);
            logger.info("Token payload keys: {}", payload.keySet());
            logger.info("Token payload: {}", payload);
            logger.info("checkIss: {}", checkIss((String) payload.get("iss")));
            if (MapUtils.isNotEmpty(payload) && checkIss((String) payload.get("iss"))) {
                userId = (String) payload.get(Constants.SUB);
                if (StringUtils.isNotBlank(userId)) {
                    int pos = userId.lastIndexOf(":");
                    userId = userId.substring(pos + 1);
                }
                tokenData.put("userId", userId);
                tokenData.put("org", payload.get("org"));
            }
        } catch (Exception ex) {
            logger.error("Exception in verifyUserAccessToken: verify ", ex);
        }
        logger.info("Token data: {}", tokenData);
        return tokenData;
    }

    private boolean checkIss(String iss) {
        String realmUrl = cache.getProperty(Constants.SSO_URL) + "realms/" + cache.getProperty(Constants.SSO_REALM);
        if (StringUtils.isBlank(realmUrl))
            return false;
        return (realmUrl.equalsIgnoreCase(iss));
    }

    private boolean isExpired(Integer expiration) {
        int currentTime = Time.currentTime();
        boolean retValue = (currentTime > expiration);
        if (retValue) {
            logger.warn(String.format("Received expired auth token request. Current time: {}, Token expire time: {}",
                    currentTime, expiration));
        }
        return retValue;
    }

    private byte[] decodeFromBase64(String data) {
        return Base64Util.decode(data, 11);
    }

}
