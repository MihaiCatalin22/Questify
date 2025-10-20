package com.questify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;



import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final StorageProperties props;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean public String questifyBucket() { return props.getBucket(); }
    @Bean public String publicBaseUrl()  { return props.getPublicBaseUrl(); }

    @Bean
    public CommandLineRunner ensureBucket(S3Client s3) {
        return args -> {
            try {
                s3.headBucket(h -> h.bucket(props.getBucket()));
            } catch (Exception e) {
                log.info("Bucket '{}' not found. Creating…", props.getBucket());
                s3.createBucket(b -> b.bucket(props.getBucket()));
            }
        };
    }

    /** DEV ONLY: allow browser PUTs from https://localhost:5173 */
    @Bean
    public CommandLineRunner setDevCors(S3Client s3) {
        return args -> {
            // Build permissive CORS for local dev
            CORSRule rule = CORSRule.builder()
                    .allowedOrigins("https://localhost:5173")
                    .allowedMethods("GET", "PUT", "HEAD", "POST", "DELETE") // <- String varargs
                    .allowedHeaders("*")
                    .exposeHeaders("ETag", "Location")
                    .maxAgeSeconds(3600)
                    .build();

            try {
                // Use the consumer overload to avoid ambiguity
                s3.putBucketCors(b -> b
                        .bucket(props.getBucket())
                        .corsConfiguration(c -> c.corsRules(rule)));
                log.info("Applied dev CORS policy to bucket '{}'.", props.getBucket());
            } catch (Exception e) {
                // Non-fatal in dev: log enough to diagnose
                log.warn("Could not apply CORS to bucket '{}': {}", props.getBucket(), e.toString());
            }
        };
    }
}
