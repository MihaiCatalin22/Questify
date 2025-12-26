package com.questify.service;

import java.io.InputStream;

public interface ProofStorageService {
    String presignPut(String objectKey, String contentType, long expiresSeconds);
    String presignGet(String objectKey, long expiresSeconds);
    void delete(String objectKey);
    long deleteByPrefix(String prefix);
    void put(String objectKey, InputStream in, long contentLength, String contentType);
}
