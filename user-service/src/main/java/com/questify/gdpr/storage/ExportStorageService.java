package com.questify.gdpr.storage;

import com.questify.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ExportStorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public void putBytes(String key, byte[] data, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    public byte[] getBytes(String key) {
        ResponseBytes<GetObjectResponse> res = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build());
        return res.asByteArray();
    }

    public void deleteObject(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build());
    }

    public String presignGet(String key, long expiresSeconds) {
        var presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresSeconds))
                .getObjectRequest(b -> b.bucket(props.getBucket()).key(key))
                .build();

        String signed = presigner.presignGetObject(presignReq).url().toString();
        return toPublic(signed);
    }


    private String toPublic(String signedUrl) {
        String pubBase = props.getPublicEndpoint();
        if (!StringUtils.hasText(pubBase)) return signedUrl;

        URI signed = URI.create(signedUrl);
        URI pub = URI.create(pubBase);

        String basePath = normalizeBasePath(pub.getRawPath());
        String signedPath = signed.getRawPath();

        String finalPath;
        if (!StringUtils.hasText(basePath) ||
                signedPath.startsWith(basePath + "/") ||
                signedPath.equals(basePath)) {
            finalPath = signedPath;
        } else {
            boolean needSlash = !basePath.endsWith("/") && (signedPath == null || !signedPath.startsWith("/"));
            finalPath = basePath + (needSlash ? "/" : "") + (signedPath == null ? "" : signedPath);
        }

        return UriComponentsBuilder.newInstance()
                .scheme(pub.getScheme())
                .host(pub.getHost())
                .port(pub.getPort())
                .path(finalPath)
                .query(signed.getRawQuery())
                .build(true)
                .toUriString();
    }

    private static String normalizeBasePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) return "";
        String p = rawPath.trim();
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
