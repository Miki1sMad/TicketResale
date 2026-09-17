package com.miki1smad.ticketresale.orders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketTokenService {

    private final String hmacSecret;

    public TicketTokenService(
            @Value("${ticketresale.tickets.hmac-secret:ticketresale-super-secure-hmac-secret-key-32bytes}")
                    String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String generateToken() {
        String uuid = UUID.randomUUID().toString();
        String hmac = computeHmac(uuid);
        return "TKT_" + uuid + "_" + hmac;
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public boolean validateTokenHmac(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("TKT_")) {
            return false;
        }

        String withoutPrefix = rawToken.substring(4);
        int separatorIdx = withoutPrefix.indexOf('_');
        if (separatorIdx <= 0 || separatorIdx >= withoutPrefix.length() - 1) {
            return false;
        }

        String uuid = withoutPrefix.substring(0, separatorIdx);
        String providedHmac = withoutPrefix.substring(separatorIdx + 1);
        String calculatedHmac = computeHmac(uuid);

        return MessageDigest.isEqual(
                providedHmac.getBytes(StandardCharsets.UTF_8), calculatedHmac.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
