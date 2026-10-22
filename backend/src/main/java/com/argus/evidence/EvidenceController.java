package com.argus.evidence;

import com.argus.common.ApiErrorException;
import com.argus.security.Permissions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Protected file-upload API for analyst evidence (validated, hashed, audited). */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final UploadService uploadService;
    private final UploadedFileRepository repository;

    public EvidenceController(UploadService uploadService, UploadedFileRepository repository) {
        this.uploadService = uploadService;
        this.repository = repository;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.EVIDENCE_UPLOAD + "')")
    public UploadedFile upload(@RequestParam("file") MultipartFile file) {
        return uploadService.store(file, actor());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.EVIDENCE_READ + "')")
    public List<UploadedFile> list() {
        return repository.findTop100ByOrderByTsDesc();
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('" + Permissions.EVIDENCE_READ + "')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) throws Exception {
        UploadedFile file = repository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("Evidence file"));
        Path path = uploadService.pathFor(file);
        byte[] bytes = Files.readAllBytes(path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getOriginalName().replace("\"", "'")).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.EVIDENCE_UPLOAD + "')")
    public void delete(@PathVariable Long id) {
        UploadedFile file = repository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("Evidence file"));
        try {
            Files.deleteIfExists(uploadService.pathFor(file));
        } catch (Exception e) {
            // storage cleanup is best effort - the record removal below still applies
        }
        repository.delete(file);
    }

    private String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "unknown" : auth.getName();
    }
}
