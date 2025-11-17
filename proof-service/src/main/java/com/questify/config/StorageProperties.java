package com.questify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String endpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String publicBaseUrl;
    private Integer putExpirySeconds = 900;
    private Integer getExpirySeconds = 900;
}
