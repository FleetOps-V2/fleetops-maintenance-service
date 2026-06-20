package com.fleetops.maintenance.controller;

import com.fleetops.maintenance.service.AlarmBroadcastService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AlarmBroadcastController {

    private final AlarmBroadcastService broadcastService;

    public AlarmBroadcastController(AlarmBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @PostMapping("/api/tasks/alarms/broadcast")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Map<String, Integer>> broadcast(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        String token = authHeader != null ? authHeader : extractTokenFromCookie(httpRequest);
        Map<String, Integer> result = broadcastService.broadcastAlarms(token);
        return ResponseEntity.ok(result);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jwt".equals(cookie.getName())) return "Bearer " + cookie.getValue();
            }
        }
        return null;
    }
}
