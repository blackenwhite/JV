package com.nabajyoti.systemdesign.week3.s3presign;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class PresignedGet {

    private static final String ACCESS_KEY = "minioadmin";
    private static final String REGION = "us-east-1";
    private static final String HOST = "localhost:9000";
    private static final String CANONICAL_URI = "/uploads/hello.txt";
    private static final DateTimeFormatter AMZ_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String DATE_STAMP = "20261106";
    private static final String EXPIRES_SECONDS = "600";

    private static final String SECRET_KEY = "minioadmin";

    public static void main(String[] args) {
        String putUrl = presign("PUT", "/uploads/from-curl.txt", 600);
        String getUrl = presign("GET", "/uploads/from-curl.txt", 5);

        System.out.println("PUT url:");
        System.out.println(putUrl);
        System.out.println("GET url (5 seconds):");
        System.out.println(getUrl);
    }

    static String uriEncode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append(String.format("%%%02X", b & 0xFF));
            }
        }
        return out.toString();
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return hex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b & 0xFF));
        }
        return out.toString();
    }

    static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] signingKey(String secretKey, String dateStamp, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    static String presign(String method, String canonicalUri, int expiresSeconds) {
        String amzDate = AMZ_DATE_FMT.format(Instant.now());
        String dateStamp = amzDate.substring(0, 8);
        String credential = ACCESS_KEY + "/" + dateStamp + "/" + REGION + "/s3/aws4_request";

        Map<String, String> query = new TreeMap<>();
        query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        query.put("X-Amz-Credential", credential);
        query.put("X-Amz-Date", amzDate);
        query.put("X-Amz-Expires", Integer.toString(expiresSeconds));
        query.put("X-Amz-SignedHeaders", "host");

        String canonicalQuery = query.entrySet().stream()
                .map(e -> uriEncode(e.getKey()) + "=" + uriEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        String canonicalHeaders = "host:" + HOST + "\n";
        String canonicalRequest = String.join("\n",
                method,
                canonicalUri,
                canonicalQuery,
                canonicalHeaders,
                "host",
                "UNSIGNED-PAYLOAD");

        String credentialScope = dateStamp + "/" + REGION + "/s3/aws4_request";
        String stringToSign = String.join("\n",
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest));

        byte[] key = signingKey(SECRET_KEY, dateStamp, REGION, "s3");
        String signature = hex(hmacSha256(key, stringToSign));

        return "http://" + HOST + canonicalUri
                + "?" + canonicalQuery
                + "&X-Amz-Signature=" + signature;
    }
}
