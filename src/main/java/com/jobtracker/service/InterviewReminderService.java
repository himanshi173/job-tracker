package com.jobtracker.service;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.repository.JobRepository;
import com.jobtracker.auth.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class InterviewReminderService {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private MailService mailService;

    // Run every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkUpcomingInterviews() {
        System.out.println("[Scheduler] Checking for upcoming interviews scheduled for today or tomorrow...");
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        List<JobApplication> jobs = jobRepository.findAll();

        int remindersSent = 0;
        for (JobApplication job : jobs) {
            if (job.getInterviewDate() != null) {
                LocalDate interviewDate = job.getInterviewDate();
                if (interviewDate.equals(today) || interviewDate.equals(tomorrow)) {
                    User user = job.getUser();
                    if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                        String timeframe = interviewDate.equals(today) ? "TODAY" : "TOMORROW";
                        mailService.sendInterviewReminder(
                                user.getEmail(),
                                user.getUsername(),
                                job.getCompanyName(),
                                job.getRole(),
                                interviewDate.toString() + " (" + timeframe + ")"
                        );
                        remindersSent++;
                    }
                }
            }
        }
        System.out.println("[Scheduler] Checked upcoming interviews. Reminders processed: " + remindersSent);
    }

    // Run every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkPendingDraftApplications() {
        System.out.println("[Scheduler] Checking for pending draft applications created yesterday...");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<JobApplication> jobs = jobRepository.findAll();

        int draftsSent = 0;
        for (JobApplication job : jobs) {
            if ("draft".equalsIgnoreCase(job.getStatus()) && job.getCreatedDate() != null && job.getCreatedDate().equals(yesterday)) {
                User user = job.getUser();
                if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                    mailService.sendDraftReminder(
                            user.getEmail(),
                            user.getUsername(),
                            job.getCompanyName() != null ? job.getCompanyName() : "Unnamed Company",
                            job.getRole() != null ? job.getRole() : "Unnamed Role"
                    );
                    draftsSent++;
                }
            }
        }
        System.out.println("[Scheduler] Checked pending draft applications. Reminders processed: " + draftsSent);
    }
}
