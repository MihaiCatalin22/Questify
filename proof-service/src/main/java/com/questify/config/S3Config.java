package com.questify.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration @RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final StorageProperties props;

    @Bean
    public S3Client s3Client() {
        var s3conf = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))             // internal
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(s3conf)
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var s3conf = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        String presignEndpoint = (props.getPublicEndpoint() != null && !props.getPublicEndpoint().isBlank())
                ? props.getPublicEndpoint()
                : props.getEndpoint();
        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))                 // 🔁 public (tailscale) host
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(s3conf)
                .build();
    }

    @Bean public String questifyBucket() { return props.getBucket(); }
    @Bean public String publicBaseUrl()  { return props.getPublicBaseUrl(); }

    @Bean
    @Profile("dev")
    public CommandLineRunner ensureBucket(S3Client s3) {
        return args -> {
            try {
                s3.headBucket(b -> b.bucket(props.getBucket()));
            } catch (Exception e) {
                log.info("Bucket '{}' not found. Creating…", props.getBucket());
                try {
                    s3.createBucket(b -> b.bucket(props.getBucket()));
                } catch (Exception ce) {
                    log.warn("Create bucket failed: {}", ce.toString());
                }
            }
        };
    }

    @Bean
    @Profile({"dev","tailscale","minio"}) // choose profiles that make sense in your env
    public CommandLineRunner setBucketCors(S3Client s3) {
        return args -> {
            var origins = props.getCorsAllowedOrigins();
            if (origins == null || origins.isEmpty()) {
                log.info("No storage.cors.allowed-origins configured; skipping bucket CORS.");
                return;
            }
            var rule = CORSRule.builder()
                    .allowedOrigins(origins)                 // e.g. https://questify.tail03c40b.ts.net
                    .allowedMethods("GET","PUT","HEAD","POST","DELETE")
                    .allowedHeaders("*")
                    .exposeHeaders("ETag","Location")
                    .maxAgeSeconds(3600)
                    .build();
            try {
                s3.putBucketCors(b -> b.bucket(props.getBucket())
                        .corsConfiguration(c -> c.corsRules(rule)));
                log.info("Applied bucket CORS for origins: {}", origins);
            } catch (Exception e) {
                log.warn("Could not apply bucket CORS: {}", e.toString());
            }
        };
    }
}
