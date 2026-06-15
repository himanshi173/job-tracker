package com.jobtracker.controller;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.service.JobService;
import com.jobtracker.auth.User;
import com.jobtracker.auth.UserRepository;
import com.jobtracker.service.GeminiService;
import com.jobtracker.service.MailService;
import com.jobtracker.service.InterviewReminderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    @Autowired
    private JobService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InterviewReminderService interviewReminderService;

    // ✅ ADD JOB (USER BASED)
    @PostMapping
    public JobApplication addJob(@RequestBody JobApplication job,
                                 @RequestParam String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        job.setUser(user);

        return service.save(job);
    }

    // ✅ GET JOBS (ONLY LOGGED USER)
    @GetMapping
    public List<JobApplication> getJobs(@RequestParam String userId) {
        return service.getByUser(userId);
    }

    // ✅ UPDATE JOB
    @PutMapping("/{id}")
    public JobApplication updateJob(@PathVariable String id,
                                    @RequestBody JobApplication job) {
        return service.update(id, job);
    }

    // ✅ DELETE JOB
    @DeleteMapping("/{id}")
    public void deleteJob(@PathVariable String id) {
        service.delete(id);
    }

    // ✅ FILTER BY STATUS (OPTIONAL USER BASED)
    @GetMapping("/status/{status}")
    public List<JobApplication> getByStatus(@PathVariable String status) {
        return service.getByStatus(status);
    }

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private MailService mailService;

    private final Path storageLocation = Paths.get("uploads/resumes").toAbsolutePath().normalize();

    private void initStorage() {
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload folder", e);
        }
    }

    // ✅ UPLOAD RESUME
    @PostMapping("/{id}/resume")
    public ResponseEntity<?> uploadResume(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        initStorage();
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType) || 
                       (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf"));
        if (!isPdf) {
            return ResponseEntity.badRequest().body("Only PDF files are supported");
        }

        try {
            JobApplication job = service.getById(id);
            
            // Generate distinct filename
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = id + "_" + System.currentTimeMillis() + fileExtension;
            Path targetPath = this.storageLocation.resolve(newFilename);

            // Copy file to disk
            Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Save old path to delete it later if it exists
            String oldPath = job.getResumePath();
            if (oldPath != null && !oldPath.isEmpty()) {
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                } catch (Exception ignored) {}
            }

            job.setResumePath(targetPath.toString());
            job.setResumeFilename(originalFilename);
            
            // Clear cached analysis since resume changed
            job.setResumeScore(null);
            job.setAiAnalysisJson(null);

            JobApplication updated = service.update(id, job);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error uploading file: " + e.getMessage());
        }
    }

    // ✅ DOWNLOAD RESUME
    @GetMapping("/{id}/resume")
    public ResponseEntity<?> downloadResume(@PathVariable String id) {
        try {
            JobApplication job = service.getById(id);
            if (job.getResumePath() == null || job.getResumePath().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Path file = Paths.get(job.getResumePath());
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                String originalName = job.getResumeFilename() != null ? job.getResumeFilename() : "resume.pdf";
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + originalName + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error retrieving file: " + e.getMessage());
        }
    }

    // ✅ DELETE RESUME
    @DeleteMapping("/{id}/resume")
    public ResponseEntity<?> deleteResume(@PathVariable String id) {
        try {
            JobApplication job = service.getById(id);
            if (job.getResumePath() == null || job.getResumePath().isEmpty()) {
                return ResponseEntity.ok().body("No resume linked to delete");
            }

            Path file = Paths.get(job.getResumePath());
            Files.deleteIfExists(file);

            job.setResumePath(null);
            job.setResumeFilename(null);
            job.setResumeScore(null);
            job.setAiAnalysisJson(null);

            JobApplication updated = service.update(id, job);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting file: " + e.getMessage());
        }
    }

    // ✅ AI RESUME ANALYZE
    @PostMapping("/{id}/analyze")
    public ResponseEntity<?> analyzeJobResume(@PathVariable String id) {
        try {
            JobApplication job = service.getById(id);
            if (job.getResumePath() == null || job.getResumePath().isEmpty()) {
                return ResponseEntity.badRequest().body("Please upload a resume first");
            }
            if (job.getJobDescription() == null || job.getJobDescription().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Please add a Job Description to compare against");
            }

            Path file = Paths.get(job.getResumePath());
            String resumeText;
            try (InputStream in = Files.newInputStream(file)) {
                resumeText = geminiService.extractTextFromPdf(in);
            } catch (Exception e) {
                return ResponseEntity.status(500).body("Failed to read PDF resume: " + e.getMessage());
            }

            // Call Gemini
            String rawJsonAnalysis = geminiService.analyzeResume(resumeText, job.getJobDescription());

            // Parse score out of JSON to cache it
            int score = 0;
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(rawJsonAnalysis);
                score = root.path("score").asInt();
            } catch (Exception ignored) {}

            job.setResumeScore(score);
            job.setAiAnalysisJson(rawJsonAnalysis);

            JobApplication updated = service.update(id, job);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("AI Analysis error: " + e.getMessage());
        }
    }

    // ✅ TEST EMAIL NOTIFICATION
    @PostMapping("/{id}/test-email")
    public ResponseEntity<?> testEmailNotification(@PathVariable String id) {
        try {
            JobApplication job = service.getById(id);
            User user = job.getUser();
            if (user == null || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("No email configured for this application's user");
            }

            String dateStr = job.getInterviewDate() != null ? job.getInterviewDate().toString() : "Not scheduled yet";
            mailService.sendInterviewReminder(
                    user.getEmail(),
                    user.getUsername(),
                    job.getCompanyName(),
                    job.getRole(),
                    dateStr
            );

            return ResponseEntity.ok(java.util.Map.of("message", "Test reminder email dispatched for user: " + user.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error dispatching test email: " + e.getMessage());
        }
    }

    // ✅ MANUAL TRIGGER DRAFT REMINDERS FOR TESTING
    @PostMapping("/test-draft-reminders")
    public ResponseEntity<?> testDraftReminders() {
        try {
            interviewReminderService.checkPendingDraftApplications();
            return ResponseEntity.ok(java.util.Map.of("message", "Draft reminders check triggered manually."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error triggering draft reminders: " + e.getMessage());
        }
    }
}