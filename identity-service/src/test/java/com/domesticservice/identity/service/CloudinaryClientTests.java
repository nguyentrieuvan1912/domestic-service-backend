package com.domesticservice.identity.service;
import com.domesticservice.identity.config.CloudinaryProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class CloudinaryClientTests {
    @Test void signatureUsesSortedParametersAndSecretSuffix() throws Exception{
        var client=new CloudinaryClient(new CloudinaryProperties("cloud","key","secret"),new ObjectMapper());
        String expected=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("public_id=asset&timestamp=123secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(expected,client.signature(Map.of("timestamp","123","public_id","asset")));
        assertFalse(new CloudinaryClient(new CloudinaryProperties("cloud","key",""),new ObjectMapper()).ready());
    }
}
