package com.argus.evidence;

import com.argus.audit.AuditWriter;
import com.argus.common.ApiErrorException;
import com.argus.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validated evidence upload: allow-list extension, size cap, content sanity checks (no NUL bytes,
 * no script tags / shebangs in text, JSON must parse), path-traversal-proof storage under a
 * server-generated UUID name.
 */
@Service
public class UploadService {

    private static final List<String> TEXT_EXTENSIONS = List.of("json", "csv", "txt", "log", "md");

    private final AppProperties props;
    private final UploadedFileRepository repository;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private Path uploadDir;

    public UploadService(AppProperties props, UploadedFileRepository repository,
                         AuditWriter auditWriter, ObjectMapper objectMapper) {
        this.props = props;
        this.repository = repository;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            uploadDir = Paths.get(props.getUpload().getDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create upload directory", e);
        }
    }

    @Transactional
    public UploadedFile store(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) {
            throw ApiErrorException.badRequest("No file uploaded");
        }
        long max = props.getUpload().getMaxBytes();
        if (file.getSize() > max) {
            throw ApiErrorException.badRequest("File exceeds the " + max + " byte upload limit");
        }
        String safeName = sanitize(file.getOriginalFilename());
        String extension = extensionOf(safeName);
        List<String> allowed = props.getUpload().getAllowedExtensions().stream()
                .map(e -> e.toLowerCase(Locale.ROOT))
                .toList();
        if (extension.isEmpty() || !allowed.contains(extension)) {
            throw ApiErrorException.badRequest("File type not allowed. Permitted: "
                    + String.join(", ", allowed));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiErrorException.badRequest("Unable to read uploaded file");
        }
        validateContent(extension, bytes);

        String storedName = UUID.randomUUID() + "." + extension;
        try {
            Path target = uploadDir.resolve(storedName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw ApiErrorException.badRequest("Invalid storage path");
            }
            Files.copy(new java.io.ByteArrayInputStream(bytes), target,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw ApiErrorException.badRequest("Unable to store uploaded file");
        }

        UploadedFile uploaded = new UploadedFile();
        uploaded.setOriginalName(safeName);
        uploaded.setStoredName(storedName);
        uploaded.setContentType(file.getContentType());
        uploaded.setSizeBytes(bytes.length);
        uploaded.setSha256(sha256(bytes));
        uploaded.setUploadedBy(actor);
        uploaded.setKind(extension.toUpperCase(Locale.ROOT));
        UploadedFile saved = repository.save(uploaded);

        auditWriter.record("evidence", "upload",
                "Evidence file accepted: " + safeName + " (" + bytes.length + " bytes, sha256 "
                        + abbreviate(saved.getSha256(), 12) + "…)",
                "ALLOWED", 0, actor, safeName);
        return saved;
    }

    private void validateContent(String extension, byte[] bytes) {
        for (int i = 0; i < Math.min(bytes.length, 4096); i++) {
            if (bytes[i] == 0) {
                throw ApiErrorException.badRequest("File contains binary control bytes");
            }
        }
        if (!TEXT_EXTENSIONS.contains(extension)) {
            return;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("<script") || lower.startsWith("#!") || lower.contains("${jndi:")) {
            throw ApiErrorException.badRequest("File content rejected (script or payload signature)");
        }
        if ("json".equals(extension)) {
            try {
                objectMapper.readTree(text);
            } catch (Exception e) {
                throw ApiErrorException.badRequest("JSON file is not well-formed");
            }
        }
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "upload.bin";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        base = slash >= 0 ? base.substring(slash + 1) : base;
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.startsWith(".")) {
            base = "file" + base;
        }
        if (base.length() > 180) {
            base = base.substring(base.length() - 180);
        }
        return base.isBlank() ? "upload.bin" : base;
    }

    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public Path pathFor(UploadedFile file) {
        Path path = uploadDir.resolve(file.getStoredName()).normalize();
        if (!path.startsWith(uploadDir) || !Files.exists(path)) {
            throw ApiErrorException.notFound("Stored file");
        }
        return path;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
