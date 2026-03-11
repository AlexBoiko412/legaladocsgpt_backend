package com.legaldocsgpt.shared.services;

import com.legaldocsgpt.shared.dto.EditTokenClaims;
import com.legaldocsgpt.shared.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class EditTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TTL_MS = 3_600_000L;

    @Value("${edit.token.secret}")
    private String secret;

    public String generate(String jobId, String userId) {
        long exp = System.currentTimeMillis() + TTL_MS;
        String payload = jobId + ":" + userId + ":" + exp;
        String sig = hmac(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + sig).getBytes());
    }

    public EditTokenClaims verify(String token) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Edit token is malformed");
        }

        int lastColon = decoded.lastIndexOf(':');
        int secondLastColon = decoded.lastIndexOf(':', lastColon - 1);

        if (lastColon < 0 || secondLastColon < 0) {
            throw new UnauthorizedException("Edit token is malformed");
        }

        String sig = decoded.substring(lastColon + 1);
        String payloadPart = decoded.substring(0, lastColon);

        long exp;
        try {
            exp = Long.parseLong(decoded.substring(secondLastColon + 1, lastColon));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Edit token is malformed");
        }

        if (System.currentTimeMillis() > exp) {
            throw new UnauthorizedException("Edit token has expired");
        }

        String expectedSig = hmac(payloadPart);
        if (!MessageDigest.isEqual(sig.getBytes(), expectedSig.getBytes())) {
            throw new UnauthorizedException("Edit token signature is invalid");
        }

        String[] parts = payloadPart.split(":", 3);
        if (parts.length != 3) {
            throw new UnauthorizedException("Edit token is malformed");
        }

        return new EditTokenClaims(parts[0], parts[1]);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid HMAC key configuration", e);
        }
    }
}