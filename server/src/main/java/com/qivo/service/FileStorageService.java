package com.qivo.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String upload(MultipartFile file);

    String uploadAvatar(MultipartFile file);
}
