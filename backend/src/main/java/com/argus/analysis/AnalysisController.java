package com.argus.analysis;

import com.argus.audit.AuditWriter;
import com.argus.common.ApiErrorException;
import com.argus.config.AppProperties;
import com.argus.security.Permissions;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Analyse Input API: untrusted prompt / PDF submission. Content analysis is advisory; the gateway
 * preview shows the independent policy decision for the implied operation.
 */
@RestController
@RequestMapping("/api/analyse")
public class AnalysisController {

    private final TextAnalysisService analysisService;
    private final PdfTextExtractor pdfTextExtractor;
    private final ContentAnalysisRepository repository;
    private final AppProperties props;
    private final AuditWriter auditWriter;

    public AnalysisController(TextAnalysisService analysisService, PdfTextExtractor pdfTextExtractor,
                              ContentAnalysisRepository repository, AppProperties props,
                              AuditWriter auditWriter) {
        this.analysisService = analysisService;
        this.pdfTextExtractor = pdfTextExtractor;
        this.repository = repository;
        this.props = props;
        this.auditWriter = auditWriter;
    }

    @PostMapping("/prompt")
    @PreAuthorize("hasAuthority('" + Permissions.ANALYSIS_CREATE + "')")
    public AnalysisDtos.AnalysisResult analysePrompt(
            @Valid @RequestBody AnalysisDtos.PromptRequest request) {
        String text = request.content();
        if (text.strip().length() < 3) {
            throw ApiErrorException.badRequest("Enter a prompt, e-mail or document text to analyse");
        }
        return analysisService.analyse(text, "PROMPT", null, actor());
    }

    @PostMapping("/pdf")
    @PreAuthorize("hasAuthority('" + Permissions.ANALYSIS_CREATE + "')")
    public AnalysisDtos.AnalysisResult analysePdf(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiErrorException.badRequest("No file uploaded");
        }
        long max = props.getUpload().getMaxBytes();
        if (file.getSize() > max) {
            throw ApiErrorException.badRequest("File exceeds the "
                    + (max / (1024 * 1024)) + "MB upload limit");
        }
        byte[] bytes = file.getBytes();
        pdfTextExtractor.validate(bytes, file.getOriginalFilename());
        PdfTextExtractor.Extracted extracted = pdfTextExtractor.extract(bytes);
        if (extracted.text().isBlank()) {
            throw ApiErrorException.badRequest(extracted.warning() == null
                    ? "No text could be extracted from the PDF"
                    : extracted.warning());
        }
        AnalysisDtos.AnalysisResult result = analysisService.analyse(extracted.text(), "PDF",
                sanitizeFilename(file.getOriginalFilename()), actor());
        return result;
    }

    /** Demo Lab asset: a locally generated PDF containing simulated malicious instructions. */
    @GetMapping("/demo-pdf")
    @PreAuthorize("hasAuthority('" + Permissions.ANALYSIS_CREATE + "')")
    public ResponseEntity<byte[]> demoPdf() {
        byte[] pdf = pdfTextExtractor.buildMaliciousDemoPdf();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("argus-malicious-demo.pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
    public List<ContentAnalysis> history(@RequestParam(name = "size", defaultValue = "30") int size) {
        return repository.findAllByOrderByTsDesc(PageRequest.of(0, Math.min(size, 100)))
                .getContent();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
    public ContentAnalysis get(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiErrorException.notFound("Analysis"));
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "upload.pdf";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        base = slash >= 0 ? base.substring(slash + 1) : base;
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return base.length() > 180 ? base.substring(base.length() - 180) : base;
    }

    private String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "unknown" : auth.getName();
    }
}
