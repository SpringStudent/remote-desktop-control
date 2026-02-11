package io.github.springstudent.dekstop.common.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HmacSHA256 signing helper for signaling commands.
 */
public class P2pSignUtils {
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private P2pSignUtils() {
    }

    public static String sign(String token, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("sign failed", e);
        }
    }

    public static boolean verify(String token, String data, String signature) {
        if (token == null || data == null || signature == null) {
            return false;
        }
        return sign(token, data).equalsIgnoreCase(signature);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
