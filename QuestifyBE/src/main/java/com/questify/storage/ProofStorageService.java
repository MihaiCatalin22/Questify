package com.questify.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ProofStorageService {
    String store(MultipartFile file, String keyPrefix);
}
