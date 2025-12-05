package com.questify.service;

import com.questify.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

@Slf4j
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

        String signed = presigner.presignPutObject(req).url().toString();
        String out = toPublic(signed);
        if (!signed.equals(out)) {
            log.debug("Rewrote presign PUT host: {} -> {}", signed, out);
        }
        return out;
    }

    @Override
    public String presignGet(String objectKey, long expiresSeconds) {
        var req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresSeconds))
                .getObjectRequest(g -> g.bucket(props.getBucket()).key(objectKey))
                .build();

        String signed = presigner.presignGetObject(req).url().toString();
        String out = toPublic(signed);
        if (!signed.equals(out)) {
            log.debug("Rewrote presign GET host: {} -> {}", signed, out);
        }
        return out;
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
                .contentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream")
                .build();
        s3.putObject(put, RequestBody.fromInputStream(in, contentLength));
    }

    private static String normalizeBasePath(String p) {
        if (!StringUtils.hasText(p)) return "";
        String out = p.startsWith("/") ? p : "/" + p;
        if (out.length() > 1 && out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
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
            boolean needSlash =
                    !basePath.endsWith("/") && (signedPath == null || !signedPath.startsWith("/"));
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
}
