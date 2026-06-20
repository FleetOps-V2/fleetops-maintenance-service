package com.fleetops.maintenance.controller;

import com.fleetops.maintenance.service.AlarmBroadcastService;
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
            @RequestHeader("Authorization") String authHeader) {
        Map<String, Integer> result = broadcastService.broadcastAlarms(authHeader);
        return ResponseEntity.ok(result);
    }
}
