package com.smartparking.identity.infrastructure.cloudinary;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VerificationCloudinaryClient {
    private static final String DELIVERY_TYPE = "authenticated";
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient http;
    private final String cloud;
    private final String key;
    private final String secret;

    @Autowired
    public VerificationCloudinaryClient(
            @Value("${app.verification.cloudinary.cloud-name:}") String cloud,
            @Value("${app.verification.cloudinary.api-key:}") String key,
            @Value("${app.verification.cloudinary.api-secret:}") String secret) {
        this(cloud, key, secret, RestClient.builder().requestFactory(requestFactory()).build());
    }

    VerificationCloudinaryClient(String cloud, String key, String secret, RestClient http) {
        this.cloud = cloud;
        this.key = key;
        this.secret = secret;
        this.http = http;
    }

    public void upload(String publicId, String format, byte[] bytes) {
        requireCredentials();
        var form = signedForm(Map.of("public_id", publicId, "type", DELIVERY_TYPE, "overwrite", "false", "backup", "false"));
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "document." + format;
            }
        });
        try {
            var result = post("upload", form, MediaType.MULTIPART_FORM_DATA);
            if (result == null || !publicId.equals(result.get("public_id")) || !DELIVERY_TYPE.equals(result.get("type"))
                    || !"image".equals(result.get("resource_type")) || !format.equals(result.get("format"))) {
                throw failure("upload response");
            }
        } catch (RuntimeException error) {
            throw failure("upload", error);
        }
    }

    public byte[] download(String publicId, String format, int expectedSize) {
        requireCredentials();
        var params = signedParams(Map.of("public_id", publicId, "type", DELIVERY_TYPE, "format", format,
                "expires_at", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond())));
        var uri = UriComponentsBuilder.fromUri(endpoint("download"));
        params.forEach(uri::queryParam);
        try {
            return http.get().uri(uri.build().encode().toUri()).exchange((request, response) -> {
                if (response.getStatusCode() != HttpStatus.OK) {
                    throw failure("download response");
                }
                byte[] bytes = response.getBody().readNBytes(expectedSize + 1);
                if (bytes.length != expectedSize) {
                    throw failure("download size");
                }
                return bytes;
            });
        } catch (RuntimeException error) {
            throw failure("download", error);
        }
    }

    public void destroy(String publicId) {
        requireCredentials();
        var form = signedForm(Map.of("public_id", publicId, "type", DELIVERY_TYPE, "invalidate", "true"));
        try {
            var result = post("destroy", form, MediaType.APPLICATION_FORM_URLENCODED);
            Object outcome = result == null ? null : result.get("result");
            if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
                throw failure("destroy response");
            }
        } catch (RuntimeException error) {
            throw failure("destroy", error);
        }
    }

    private Map<String, Object> post(String action, MultiValueMap<String, Object> form, MediaType type) {
        return http.post().uri(endpoint(action)).contentType(type).body(form).retrieve().body(RESPONSE_TYPE);
    }

    private MultiValueMap<String, Object> signedForm(Map<String, String> fields) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        signedParams(fields).forEach(form::add);
        return form;
    }

    private Map<String, String> signedParams(Map<String, String> fields) {
        var params = new TreeMap<>(fields);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        String payload = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            params.put("signature", HexFormat.of().formatHex(digest.digest((payload + secret).getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
        params.put("api_key", key);
        return params;
    }

    private URI endpoint(String action) {
        return URI.create("https://api.cloudinary.com/v1_1/" + cloud + "/image/" + action);
    }

    private void requireCredentials() {
        if (!isConfigured()) {
            throw failure("configuration");
        }
    }

    public boolean isConfigured() {
        return cloud.matches("[a-zA-Z0-9_-]+") && !key.isBlank() && !secret.isBlank();
    }

    private AppException failure(String operation, RuntimeException error) {
        return error instanceof AppException appException ? appException : failure(operation);
    }

    private AppException failure(String operation) {
        // Cloudinary exceptions can contain signed URLs or response bodies.
        log.warn("Merchant verification Cloudinary failure ({})", operation);
        return new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Kho lưu trữ giấy tờ đang gặp sự cố. Vui lòng thử lại.");
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return factory;
    }
}
