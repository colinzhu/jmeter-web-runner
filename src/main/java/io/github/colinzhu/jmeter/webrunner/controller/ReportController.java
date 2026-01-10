package io.github.colinzhu.jmeter.webrunner.controller;

import io.github.colinzhu.jmeter.webrunner.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {
    private final ReportService reportService;

    /**
     * Serve report resources (HTML, CSS, JS, images, etc.)
     * Handles both /api/reports/{id} and /api/reports/{id}/** patterns
     */
    @GetMapping(value = {"/{id}", "/{id}/**"})
    public ResponseEntity<Resource> getReportResource(@PathVariable String id,
                                                      HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String resourcePath = fullPath.substring(fullPath.indexOf(id) + id.length());

        // Remove leading slash if present
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        // If no path specified or ends with /, default to index.html
        if (resourcePath.isEmpty() || resourcePath.equals("/")) {
            resourcePath = "index.html";
        }

        log.info("Getting report resource: {} - {}", id, resourcePath);

        Resource resource = reportService.getReportResource(id, resourcePath);

        // Determine content type based on file extension
        String contentType = determineContentType(resourcePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private String determineContentType(String path) {
        if (path == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".css")) return "text/css";
        if (lowerPath.endsWith(".js")) return "application/javascript";
        if (lowerPath.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lowerPath.endsWith(".gif")) return MediaType.IMAGE_GIF_VALUE;
        if (lowerPath.endsWith(".svg")) return "image/svg+xml";
        if (lowerPath.endsWith(".json")) return MediaType.APPLICATION_JSON_VALUE;
        if (lowerPath.endsWith(".html")) return MediaType.TEXT_HTML_VALUE;
        if (lowerPath.endsWith(".woff")) return "font/woff";
        if (lowerPath.endsWith(".woff2")) return "font/woff2";
        if (lowerPath.endsWith(".ttf")) return "font/ttf";
        if (lowerPath.endsWith(".eot")) return "application/vnd.ms-fontobject";

        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * Download report as ZIP archive
     * GET /api/reports/{id}/download
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable String id) {
        log.info("Downloading report: {}", id);

        File zipFile = reportService.packageReportAsZip(id);
        Resource resource = new FileSystemResource(zipFile);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report-" + id + ".zip\"")
                .body(resource);
    }
}
