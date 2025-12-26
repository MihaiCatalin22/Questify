package com.questify.service;

import com.questify.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    public long deleteByPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) return 0L;

        long totalDeleted = 0L;
        String token = null;

        do {
            var listReq = ListObjectsV2Request.builder()
                    .bucket(props.getBucket())
                    .prefix(prefix)
                    .continuationToken(token)
                    .maxKeys(1000)
                    .build();

            ListObjectsV2Response list = s3.listObjectsV2(listReq);

            List<S3Object> contents = list.contents() == null ? List.of() : list.contents();
            if (!contents.isEmpty()) {
                List<ObjectIdentifier> ids = new ArrayList<>(contents.size());
                for (S3Object o : contents) {
                    if (o != null && StringUtils.hasText(o.key())) {
                        ids.add(ObjectIdentifier.builder().key(o.key()).build());
                    }
                }

                if (!ids.isEmpty()) {
                    var delReq = DeleteObjectsRequest.builder()
                            .bucket(props.getBucket())
                            .delete(Delete.builder().objects(ids).quiet(true).build())
                            .build();
                    s3.deleteObjects(delReq);
                    totalDeleted += ids.size();
                }
            }

            token = list.nextContinuationToken();
            if (!list.isTruncated()) break;

        } while (true);

        return totalDeleted;
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
