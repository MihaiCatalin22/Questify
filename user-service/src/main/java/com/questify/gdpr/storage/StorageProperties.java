package com.questify.gdpr.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String endpoint;
    private String publicEndpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket;
    private Integer getExpirySeconds = 900;
}
