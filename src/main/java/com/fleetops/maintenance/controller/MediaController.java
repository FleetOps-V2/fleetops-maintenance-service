package com.fleetops.maintenance.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    @Value("${app.efs.mount-path:/var/www/fleetops/shared-media}")
    private String efsMountPath;

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCatalog() {
        Path mountDir = Paths.get(efsMountPath);
        List<Map<String, Object>> files = new ArrayList<>();

        if (!Files.exists(mountDir)) {
            return ResponseEntity.ok(files);
        }

        try (var stream = Files.list(mountDir)) {
            stream.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    String nodeName = System.getenv().getOrDefault("EC2_NODE_NAME", "ec2-node-01");
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("filename", p.getFileName().toString());
                    entry.put("uploaderNode", nodeName);
                    entry.put("size", formatSize(attrs.size()));
                    entry.put("timestamp", TS_FMT.format(Instant.ofEpochMilli(attrs.creationTime().toMillis())));
                    entry.put("mountPoint", efsMountPath);
                    // vehicle number encoded in filename prefix: <vehicleNum>_<rest>
                    String fn = p.getFileName().toString();
                    entry.put("vehicleNum", fn.contains("_") ? fn.substring(0, fn.indexOf('_')) : "unknown");
                    files.add(entry);
                } catch (IOException e) {
                    log.warn("Could not read attributes for {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.error("Failed to list EFS mount directory {}: {}", efsMountPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(files);
    }

    @GetMapping("/file/{filename}")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path file = Paths.get(efsMountPath).resolve(filename).normalize();
            if (!file.startsWith(Paths.get(efsMountPath).normalize())) {
                return ResponseEntity.badRequest().build();
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (IOException e) {
            log.error("Failed to serve file {}: {}", filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("vehicleNumber") String vehicleNumber,
            @RequestParam("ec2Node") String ec2Node,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        Path mountDir = Paths.get(efsMountPath);
        try {
            Files.createDirectories(mountDir);
        } catch (IOException e) {
            log.error("Cannot create/access EFS mount directory {}: {}", efsMountPath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "EFS mount directory not accessible: " + e.getMessage()));
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase() : ".jpg";
        if (!java.util.Set.of(".jpg", ".jpeg", ".png", ".pdf", ".mp4").contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("error", "File type not allowed. Permitted: jpg, jpeg, png, pdf, mp4"));
        }
        String safeName = vehicleNumber.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename = safeName + "_" + System.currentTimeMillis() + ext;
        Path dest = mountDir.resolve(filename).normalize();
        if (!dest.startsWith(mountDir.normalize())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid vehicle number"));
        }

        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("User {} wrote {} ({}) to EFS at {}", authentication.getName(), filename, formatSize(file.getSize()), dest);
        } catch (IOException e) {
            log.error("Failed to write file to EFS: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to write file to EFS partition: " + e.getMessage()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", filename);
        result.put("vehicleNum", vehicleNumber);
        result.put("uploaderNode", ec2Node);
        result.put("size", formatSize(file.getSize()));
        result.put("timestamp", TS_FMT.format(Instant.now()));
        result.put("mountPoint", efsMountPath);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        return String.format("%.1f MB", kb / 1024.0);
    }
}
