package com.questify.gdpr;

import com.questify.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProofMetadataExporter {

    private final S3Client s3;
    private final StorageProperties props;

    public List<Map<String, Object>> listProofObjectsForUser(String userId) {
        String prefix = "proofs/" + userId + "/";

        var res = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(props.getBucket())
                .prefix(prefix)
                .maxKeys(1000)
                .build());

        List<S3Object> contents = res.contents() == null ? List.of() : res.contents();

        return contents.stream()
                .<Map<String, Object>>map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", o.key());
                    m.put("size", o.size());
                    m.put("etag", o.eTag());
                    m.put("lastModified", o.lastModified());
                    return m;
                })
                .toList();
    }
}
