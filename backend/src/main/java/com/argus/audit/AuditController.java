package com.argus.audit;

import com.argus.event.EventDtos;
import com.argus.security.Permissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Append-only compliance audit log with full-text search and 1-click JSON/CSV export. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEntryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditController(AuditEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public record PageResponse(List<EventDtos.AuditView> items, int page, int size, long totalElements,
                               int totalPages) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_READ + "')")
    public PageResponse search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<AuditEntry> result = repository.search(query,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200)));
        List<EventDtos.AuditView> items = result.getContent().stream()
                .map(e -> EventDtos.AuditView.from(e, objectMapper))
                .toList();
        return new PageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_EXPORT + "')")
    public void export(@RequestParam(name = "format", defaultValue = "json") String format,
                       @RequestParam(name = "q", required = false) String q,
                       HttpServletResponse response) throws IOException {
        String query = q == null || q.isBlank() ? null : q.trim();
        List<AuditEntry> entries = repository.search(query, PageRequest.of(0, 5000)).getContent();
        String stamp = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.BASIC_ISO_DATE);

        if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"argus-audit-" + stamp + ".csv\"");
            StringBuilder sb = new StringBuilder();
            sb.append("timestamp,actor,agent,role,kind,description,target,decision,score,reason\r\n");
            for (AuditEntry e : entries) {
                sb.append(csv(String.valueOf(e.getTs()))).append(',')
                        .append(csv(e.getActor())).append(',')
                        .append(csv(e.getAgent())).append(',')
                        .append(csv(e.getRole())).append(',')
                        .append(csv(e.getKind())).append(',')
                        .append(csv(e.getDescription())).append(',')
                        .append(csv(e.getTarget())).append(',')
                        .append(csv(e.getDecision())).append(',')
                        .append(csv(e.getScore() == null ? "" : String.valueOf(e.getScore()))).append(',')
                        .append(csv(e.getReason())).append("\r\n");
            }
            response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }

        List<EventDtos.AuditView> views = entries.stream()
                .map(e -> EventDtos.AuditView.from(e, objectMapper))
                .toList();
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"argus-audit-" + stamp + ".json\"");
        response.getOutputStream().write(objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(views).getBytes(StandardCharsets.UTF_8));
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        if (v.contains(",") || v.contains("\"")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v.toLowerCase(Locale.ROOT).equals(v) ? v : v;
    }
}
