package io.github.springstudent.dekstop.common.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * @author ZhouNing
 * @date 2026/2/11
 **/
public class P2pSecurityUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public static String sign(String token, String content) {
        if (token == null || content == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] result = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return toHex(result);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean verify(String token, String content, String signature) {
        if (token == null || content == null || signature == null) {
            return false;
        }
        String expected = sign(token, content);
        return signature.equals(expected);
    }

    private static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte b : data) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
