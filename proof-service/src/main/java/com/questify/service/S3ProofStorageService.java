package com.questify.service;

import com.questify.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3ProofStorageService implements ProofStorageService {

    private final StorageProperties props;
    private final S3Client s3;
    private final S3Presigner presigner;

    @Override
    public String presignPut(String objectKey, String contentType, long expiresSeconds) {
        var req = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresSeconds))
                .putObjectRequest(p -> p.bucket(props.getBucket())
                        .key(objectKey)
                        .contentType(contentType))
                .build();
        return presigner.presignPutObject(req).url().toString();
    }

    @Override
    public String presignGet(String objectKey, long expiresSeconds) {
        var req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresSeconds))
                .getObjectRequest(g -> g.bucket(props.getBucket()).key(objectKey))
                .build();
        return presigner.presignGetObject(req).url().toString();
    }

    @Override
    public void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.getBucket())
                .key(objectKey)
                .build());
    }

    @Override
    public void put(String objectKey, InputStream in, long contentLength, String contentType) {
        var put = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(objectKey)
                .contentType(contentType != null && !contentType.isBlank()
                        ? contentType : "application/octet-stream")
                .build();
        s3.putObject(put, RequestBody.fromInputStream(in, contentLength));
    }
}