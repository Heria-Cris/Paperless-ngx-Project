package com.paperless.local.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "txt", "csv"
    );

    private final String uploadDir;
    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file, Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("文件名不合法");
        }

        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型：" + extension);
        }

        LocalDate today = LocalDate.now();
        Path subDirectory = Paths.get(
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()),
                "user_" + userId
        );
        Files.createDirectories(uploadRoot.resolve(subDirectory));

        String storedFilename = UUID.randomUUID() + "." + extension;
        Path target = uploadRoot.resolve(subDirectory).resolve(storedFilename).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("文件存储路径不合法");
        }

        file.transferTo(target);

        String storagePath = Paths.get(uploadDir).isAbsolute()
                ? target.toString()
                : Paths.get(uploadDir).resolve(subDirectory).resolve(storedFilename).toString();
        return new StoredFile(
                originalFilename,
                storedFilename,
                storagePath.replace("\\", "/"),
                file.getSize(),
                extension.toUpperCase(Locale.ROOT)
        );
    }

    public Path resolve(String storagePath) {
        Path path = Paths.get(storagePath);
        Path absolute = path.isAbsolute() ? path.normalize() : path.toAbsolutePath().normalize();
        if (!absolute.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("文件存储路径不合法");
        }
        return absolute;
    }

    public void deleteIfExists(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException | IllegalArgumentException ignored) {
            // A failed cleanup should not hide the original business error.
        }
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件必须包含扩展名");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(
            String originalFilename,
            String storedFilename,
            String storagePath,
            long fileSize,
            String fileType
    ) {
    }
}
