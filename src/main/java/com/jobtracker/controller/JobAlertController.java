package com.jobtracker.controller;

import com.jobtracker.entity.JobAlert;
import com.jobtracker.repository.JobAlertRepository;
import com.jobtracker.auth.User;
import com.jobtracker.auth.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class JobAlertController {

    @Autowired
    private JobAlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.jobtracker.service.JobAlertSchedulerService schedulerService;

    // ✅ MANUAL TRIGGER DAILY ALERTS CHECK
    @PostMapping("/test-trigger")
    public ResponseEntity<?> testTriggerAlerts() {
        try {
            schedulerService.processAllAlerts();
            return ResponseEntity.ok(Map.of("message", "Job alerts process triggered manually."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error triggering alert check: " + e.getMessage());
        }
    }

    // ✅ CREATE JOB ALERT
    @PostMapping
    public ResponseEntity<?> createAlert(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            String role = request.get("role");
            String location = request.get("location");

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("User ID is required");
            }
            if ((role == null || role.trim().isEmpty()) && (location == null || location.trim().isEmpty())) {
                return ResponseEntity.badRequest().body("Please specify at least a Role or a Location for the alert");
            }

            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }

            JobAlert alert = new JobAlert();
            alert.setUser(userOpt.get());
            alert.setRole(role != null ? role.trim() : "");
            alert.setLocation(location != null ? location.trim() : "");
            alert.setCreatedDate(LocalDate.now());

            JobAlert saved = alertRepository.save(alert);
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating alert: " + e.getMessage());
        }
    }

    // ✅ GET USER ALERTS
    @GetMapping
    public ResponseEntity<?> getAlerts(@RequestParam String userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }

            List<JobAlert> alerts = alertRepository.findByUser(userOpt.get());
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving alerts: " + e.getMessage());
        }
    }

    // ✅ DELETE ALERT
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAlert(@PathVariable String id) {
        try {
            alertRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Job alert deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting alert: " + e.getMessage());
        }
    }
}
