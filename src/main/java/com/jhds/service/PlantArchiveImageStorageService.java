package com.jhds.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/** Stores validated plant archive images outside the packaged application. */
@Service
public class PlantArchiveImageStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_IMAGE_DIMENSION = 10000;
    private static final long MAX_IMAGE_PIXELS = 40L * 1000L * 1000L;

    @Value("${archive.upload-path:./uploads/archive}")
    private String uploadPath;

    private Path uploadDirectory;

    @PostConstruct
    public void init() {
        uploadDirectory = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create archive upload directory: " + uploadDirectory, e);
        }
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("图片不能超过 10 MB");
        }

        String extension = detectImageExtension(file);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path destination = uploadDirectory.resolve(fileName).normalize();
        if (!destination.getParent().equals(uploadDirectory)) {
            throw new IOException("Invalid archive upload destination");
        }

        Path temporary = Files.createTempFile(uploadDirectory, ".upload-", ".tmp");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return fileName;
    }

    private void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String detectImageExtension(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new IllegalArgumentException("文件不是可识别的图片");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("仅支持 JPG、PNG 或 GIF 图片");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("图片尺寸超出限制");
                }
                return extensionFor(reader.getFormatName());
            } finally {
                reader.dispose();
            }
        }
    }

    private String extensionFor(String formatName) {
        String format = formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(format) || "jpg".equals(format)) return "jpg";
        if ("png".equals(format)) return "png";
        if ("gif".equals(format)) return "gif";
        throw new IllegalArgumentException("仅支持 JPG、PNG 或 GIF 图片");
    }
}
