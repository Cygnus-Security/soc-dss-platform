package com.socdss.response;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class ApprovalTokenService {
    private final byte[] secret;

    public ApprovalTokenService(@Value("${app.response.approval-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.response.approval-secret must contain at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(ResponseAction action, Instant expiresAt) {
        String payload = String.join("|",
                action.getId(),
                String.valueOf(action.getIncident().getId()),
                action.getActionType(),
                action.getTarget(),
                String.valueOf(expiresAt.getEpochSecond())
        );
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign approval token", e);
        }
    }
}
