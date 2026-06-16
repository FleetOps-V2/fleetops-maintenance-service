package com.fleetops.maintenance.controller;

import com.fleetops.maintenance.dto.TaskRequest;
import com.fleetops.maintenance.entity.MaintenanceQueue;
import com.fleetops.maintenance.entity.PendingTask;
import com.fleetops.maintenance.repository.MaintenanceQueueRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
@Transactional
public class TaskController {

    private final MaintenanceQueueRepository queueRepository;

    public TaskController(MaintenanceQueueRepository queueRepository) {
        this.queueRepository = queueRepository;
    }

    private MaintenanceQueue getOrCreateQueue(String username) {
        return queueRepository.findByUsername(username).orElseGet(() -> {
            MaintenanceQueue newQueue = new MaintenanceQueue();
            newQueue.setUsername(username);
            return queueRepository.save(newQueue);
        });
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceQueue> getQueue(Authentication authentication) {
        return ResponseEntity.ok(getOrCreateQueue(authentication.getName()));
    }

    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceQueue> addTask(
            Authentication authentication, 
            @RequestBody TaskRequest request,
            @RequestParam(required = false) String username) {
        
        String targetUser = authentication.getName();
        if (username != null && !username.isEmpty()) {
            boolean isPrivileged = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
            if (isPrivileged) {
                targetUser = username;
            }
        }

        MaintenanceQueue queue = getOrCreateQueue(targetUser);

        // Check if identical task already exists in queue to avoid duplicates
        boolean exists = queue.getTasks().stream()
                .anyMatch(t -> t.getVehicleId().equals(request.getVehicleId()) && t.getTaskType().equals(request.getTaskType()));

        if (!exists) {
            PendingTask newTask = new PendingTask();
            newTask.setVehicleId(request.getVehicleId());
            newTask.setTaskType(request.getTaskType());
            newTask.setDescription(request.getDescription());
            newTask.setQueue(queue);
            queue.getTasks().add(newTask);
            queue = queueRepository.save(queue);
        }

        return ResponseEntity.ok(queue);
    }

    @DeleteMapping("/remove/{taskId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceQueue> removeTask(Authentication authentication, @PathVariable Long taskId) {
        MaintenanceQueue queue = getOrCreateQueue(authentication.getName());
        queue.getTasks().removeIf(t -> t.getId() != null && t.getId().equals(taskId));
        return ResponseEntity.ok(queueRepository.save(queue));
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasAnyRole('DRIVER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<MaintenanceQueue> clearQueue(Authentication authentication) {
        MaintenanceQueue queue = getOrCreateQueue(authentication.getName());
        queue.getTasks().clear();
        return ResponseEntity.ok(queueRepository.save(queue));
    }
}

