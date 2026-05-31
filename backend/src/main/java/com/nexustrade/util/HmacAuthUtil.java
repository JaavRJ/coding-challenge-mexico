package com.nexustrade.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class HmacAuthUtil {

    /**
     * Generates an HMAC-SHA256 signature and returns it as a Hex string.
     * Used by Binance and Coinbase.
     */
    public static String generateHmacSha256Hex(String secret, String message) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate hmac-sha256", e);
        }
    }

    /**
     * Generates an HMAC-SHA512 signature and returns it as a Base64 string.
     * Often used by Kraken where the secret is base64 decoded first.
     */
    public static String generateKrakenSignature(String path, String nonce, String postData, String apiSecret) {
        try {
            // Kraken signature formula:
            // HMAC-SHA512(path + SHA256(nonce + postdata), base64_decode(apiSecret))
            
            // 1. SHA256(nonce + postdata)
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update((nonce + postData).getBytes(StandardCharsets.UTF_8));
            byte[] sha256Hash = md.digest();

            // 2. path + SHA256(nonce + postdata)
            byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
            byte[] hmacMessage = new byte[pathBytes.length + sha256Hash.length];
            System.arraycopy(pathBytes, 0, hmacMessage, 0, pathBytes.length);
            System.arraycopy(sha256Hash, 0, hmacMessage, pathBytes.length, sha256Hash.length);

            // 3. HMAC-SHA512 using base64 decoded secret
            Mac hmacSha512 = Mac.getInstance("HmacSHA512");
            byte[] decodedSecret = Base64.getDecoder().decode(apiSecret);
            SecretKeySpec secretKeySpec = new SecretKeySpec(decodedSecret, "HmacSHA512");
            hmacSha512.init(secretKeySpec);

            byte[] hmacResult = hmacSha512.doFinal(hmacMessage);
            return Base64.getEncoder().encodeToString(hmacResult);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate Kraken signature", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
