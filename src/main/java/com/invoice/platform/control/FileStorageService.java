package com.invoice.platform.control;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${app.storage.upload-dir}") String uploadDir) throws IOException {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    public String store(MultipartFile file) throws IOException {
        String storageKey = UUID.randomUUID() + ".pdf";
        Path target = uploadDir.resolve(storageKey);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return storageKey;
    }

    public Path resolve(String storageKey) {
        return uploadDir.resolve(storageKey).normalize();
    }
}
