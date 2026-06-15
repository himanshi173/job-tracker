package com.jobtracker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendInterviewReminder(String toEmail, String username, String companyName, String role, String date) {
        String subject = "Interview Reminder: " + companyName + " - " + role;
        String body = "Hello " + username + ",\n\n" +
                "This is a friendly reminder that you have an upcoming interview for the " + role + " position at " + companyName + ".\n\n" +
                "Scheduled Date: " + date + "\n\n" +
                "Get ready and good luck!\n\n" +
                "Best regards,\n" +
                "Job Tracker Team";

        if (mailSender == null) {
            System.out.println("==================================================");
            System.out.println("[MOCK EMAIL ALERT - SMTP Not Configured]");
            System.out.println("To: " + toEmail);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("==================================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("Interview reminder email successfully sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send email to " + toEmail + " due to: " + e.getMessage());
            System.out.println("==================================================");
            System.out.println("[MOCK EMAIL ALERT - Fallback Mode]");
            System.out.println("To: " + toEmail);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("==================================================");
        }
    }

    public void sendDraftReminder(String toEmail, String username, String companyName, String role) {
        String subject = "Action Required: Pending Job Application Draft";
        String body = "Hello " + username + ",\n\n" +
                "This is a friendly reminder that you have a pending job application draft for the " + role + " position at " + companyName + ".\n\n" +
                "Don't forget to complete your application and track your progress!\n\n" +
                "Best regards,\n" +
                "Job Tracker Team";

        if (mailSender == null) {
            System.out.println("==================================================");
            System.out.println("[MOCK EMAIL ALERT - SMTP Not Configured]");
            System.out.println("To: " + toEmail);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("==================================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("Draft reminder email successfully sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send draft reminder email to " + toEmail + " due to: " + e.getMessage());
            System.out.println("==================================================");
            System.out.println("[MOCK EMAIL ALERT - Fallback Mode]");
            System.out.println("To: " + toEmail);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("==================================================");
        }
    }

    public void sendOtpEmail(String toEmail, String username, String otp, String type) {
        String subject = "signup".equalsIgnoreCase(type) ? 
                "Verify Your Job Tracker Account - OTP" : 
                "Reset Your Job Tracker Password - OTP";
        
        String body = "Hello " + (username != null ? username : "User") + ",\n\n" +
                "Your One-Time Password (OTP) code is:\n\n" +
                "👉   " + otp + "   👈\n\n" +
                "This OTP is valid for 10 minutes. Please do not share this code with anyone.\n\n" +
                "Best regards,\n" +
                "Job Tracker Team";

        if (mailSender == null) {
            System.out.println("==================================================");
            System.out.println("[MOCK OTP EMAIL ALERT - SMTP Not Configured]");
            System.out.println("To: " + toEmail);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("==================================================");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("OTP email successfully sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send OTP email to " + toEmail + " due to: " + e.getMessage());
            System.out.println("==================================================");
            System.out.println("[MOCK OTP EMAIL ALERT - Fallback Mode]");
            System.out.println("To: " + toEmail);
            System.out.println("Subject: " + subject);
            System.out.println("Body:\n" + body);
            System.out.println("==================================================");
        }
    }
}
