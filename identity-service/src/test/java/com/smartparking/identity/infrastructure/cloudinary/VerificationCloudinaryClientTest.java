package com.smartparking.identity.infrastructure.cloudinary;

import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VerificationCloudinaryClientTest {
    private static final String ENDPOINT = "https://api.cloudinary.com/v1_1/test_cloud/image/";
    private static final String ASSET = "smartparking/merchant-kyc/test-id";
    private static final String SECRET = "test-cloudinary-secret";
    private MockRestServiceServer server;
    private VerificationCloudinaryClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VerificationCloudinaryClient("test_cloud", "test-key", SECRET, builder.build());
    }

    @Test
    void usesAuthenticatedStorageAndSignedOriginalDownload() {
        server.expect(requestTo(ENDPOINT + "upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("authenticated")))
                .andExpect(content().string(containsString("filename=\"document.png\"")))
                .andExpect(content().string(not(containsString(SECRET))))
                .andRespond(withSuccess("""
                        {"public_id":"smartparking/merchant-kyc/test-id","type":"authenticated",
                         "resource_type":"image","format":"png"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(ENDPOINT + "download?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("public_id", ASSET))
                .andExpect(queryParam("format", "png"))
                .andExpect(queryParam("type", "authenticated"))
                .andExpect(queryParam("expires_at", matchesPattern("[0-9]+")))
                .andExpect(queryParam("signature", matchesPattern("[a-f0-9]{64}")))
                .andRespond(withSuccess(new byte[] {1, 2}, MediaType.IMAGE_PNG));
        server.expect(requestTo(ENDPOINT + "destroy"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("type=authenticated")))
                .andExpect(content().string(not(containsString(SECRET))))
                .andRespond(withSuccess("{\"result\":\"not found\"}", MediaType.APPLICATION_JSON));

        client.upload(ASSET, "png", new byte[] {1, 2});
        assertThat(client.download(ASSET, "png", 2)).containsExactly(1, 2);
        client.destroy(ASSET);
        server.verify();
    }

    @Test
    void sanitizesProviderErrorsAndRejectsIncorrectByteCount() {
        server.expect(requestTo(ENDPOINT + "upload"))
                .andRespond(withServerError().body(SECRET));
        server.expect(requestTo(startsWith(ENDPOINT + "download?")))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_PNG));

        assertThatThrownBy(() -> client.upload(ASSET, "png", new byte[] {1, 2}))
                .isInstanceOf(AppException.class).hasMessageNotContaining(SECRET).hasNoCause();
        assertThatThrownBy(() -> client.download(ASSET, "png", 2)).isInstanceOf(AppException.class);
        server.verify();
    }
}
