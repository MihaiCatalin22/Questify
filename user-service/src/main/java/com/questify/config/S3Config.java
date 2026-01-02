package com.questify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final StorageProperties props;

    @Bean
    public S3Client s3Client() {
        var s3conf = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        URI internal = URI.create(props.getEndpoint());
        log.info("S3Client endpoint: {}", internal);

        return S3Client.builder()
                .endpointOverride(internal)
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(s3conf)
                .httpClient(ApacheHttpClient.builder().build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        String ep = StringUtils.hasText(props.getPublicEndpoint())
                ? props.getPublicEndpoint()
                : props.getEndpoint();

        URI raw = URI.create(ep);
        URI presignEp = sanitizeForSigV4(raw);
        if (!raw.equals(presignEp)) {
            log.warn("S3Presigner endpoint had a path component ({}). Using {} to avoid signature mismatch.", raw, presignEp);
        } else {
            log.info("S3Presigner endpoint: {}", presignEp);
        }

        var s3conf = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Presigner.builder()
                .endpointOverride(presignEp)
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(s3conf)
                .build();
    }

    private static URI sanitizeForSigV4(URI u) {
        String base = u.getScheme() + "://" + u.getHost() + (u.getPort() != -1 ? ":" + u.getPort() : "");
        return URI.create(base);
    }
}
