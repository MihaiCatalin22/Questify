package com.questify.storage;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ProofStorageService implements ProofStorageService {

    private final S3Client s3Client;
    private final String questifyBucket;
    private final String publicBaseUrl;


    @Override
    @SneakyThrows
    public String store(MultipartFile file, String keyPrefix) {
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String key = (keyPrefix == null ? "" : keyPrefix + "/")
                + Instant.now().toEpochMilli() + "-" + UUID.randomUUID()
                + (ext != null ? "." + ext : "");

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(questifyBucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .acl(ObjectCannedACL.PUBLIC_READ) // change to prived + signed in prod
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        return (publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/") + key;
    }
}
