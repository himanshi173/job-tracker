package com.jobtracker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

        sendMail(toEmail, subject, body);
    }

    public void sendDraftReminder(String toEmail, String username, String companyName, String role) {
        String subject = "Action Required: Pending Job Application Draft";
        String body = "Hello " + username + ",\n\n" +
                "This is a friendly reminder that you have a pending job application draft for the " + role + " position at " + companyName + ".\n\n" +
                "Don't forget to complete your application and track your progress!\n\n" +
                "Best regards,\n" +
                "Job Tracker Team";

        sendMail(toEmail, subject, body);
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

        sendMail(toEmail, subject, body);
    }

    private void sendMail(String toEmail, String subject, String body) {
        String sendgridApiKey = System.getenv("SENDGRID_API_KEY");
        if (sendgridApiKey == null) {
            sendgridApiKey = System.getProperty("SENDGRID_API_KEY");
        }

        if (sendgridApiKey != null && !sendgridApiKey.trim().isEmpty()) {
            try {
                String maskedKey = sendgridApiKey.length() > 12 ? 
                    sendgridApiKey.substring(0, 8) + "..." + sendgridApiKey.substring(sendgridApiKey.length() - 4) : 
                    "invalid-key";
                System.out.println("[MAIL SERVICE] Attempting to send email via SendGrid using key: " + maskedKey);
                sendEmailViaSendGrid(toEmail, subject, body, sendgridApiKey);
                return;
            } catch (Exception e) {
                System.err.println("[MAIL SERVICE] SendGrid send failed, trying Resend fallback... Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        String resendApiKey = System.getenv("RESEND_API_KEY");
        if (resendApiKey == null) {
            resendApiKey = System.getProperty("RESEND_API_KEY");
        }

        if (resendApiKey != null && !resendApiKey.trim().isEmpty()) {
            try {
                String maskedKey = resendApiKey.length() > 12 ? 
                    resendApiKey.substring(0, 8) + "..." + resendApiKey.substring(resendApiKey.length() - 4) : 
                    "invalid-key";
                System.out.println("[MAIL SERVICE] Attempting to send email via Resend using key: " + maskedKey);
                sendEmailViaResend(toEmail, subject, body, resendApiKey);
                return;
            } catch (Exception e) {
                System.err.println("[MAIL SERVICE] Resend send failed, trying Brevo fallback... Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        String brevoApiKey = System.getenv("BREVO_API_KEY");
        if (brevoApiKey == null) {
            brevoApiKey = System.getProperty("BREVO_API_KEY");
        }

        System.out.println("[MAIL SERVICE] BREVO_API_KEY defined: " + (brevoApiKey != null && !brevoApiKey.trim().isEmpty()));

        if (brevoApiKey != null && !brevoApiKey.trim().isEmpty()) {
            try {
                String maskedKey = brevoApiKey.length() > 12 ? 
                    brevoApiKey.substring(0, 8) + "..." + brevoApiKey.substring(brevoApiKey.length() - 4) : 
                    "invalid-key";
                System.out.println("[MAIL SERVICE] Attempting to send email via Brevo using key: " + maskedKey);
                sendEmailViaBrevo(toEmail, subject, body, brevoApiKey);
                return;
            } catch (Exception e) {
                System.err.println("[MAIL SERVICE] Brevo send failed, trying SMTP fallback... Error: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("[MAIL SERVICE] BREVO_API_KEY is not configured or is empty. Skipping Brevo HTTP API.");
        }

        // Standard SMTP Fallback
        if (mailSender == null) {
            printMockEmail(toEmail, subject, body, "SMTP Not Configured");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            System.out.println("Email successfully sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send email to " + toEmail + " due to: " + e.getMessage());
            printMockEmail(toEmail, subject, body, "Fallback Mode");
        }
    }

    private void sendEmailViaBrevo(String toEmail, String subject, String body, String apiKey) {
        String senderEmail = System.getenv("SMTP_USERNAME");
        if (senderEmail == null) {
            senderEmail = System.getProperty("SMTP_USERNAME");
        }
        if (senderEmail == null || senderEmail.trim().isEmpty()) {
            senderEmail = "himanshi.kh.2004@gmail.com"; // Default fallback
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // Format JSON payload
            String jsonPayload = String.format(
                "{\"sender\":{\"name\":\"Job Tracker\",\"email\":\"%s\"}," +
                "\"to\":[{\"email\":\"%s\"}]," +
                "\"subject\":\"%s\"," +
                "\"htmlContent\":\"%s\"}",
                senderEmail,
                toEmail,
                subject.replace("\"", "\\\""),
                body.replace("\n", "<br>").replace("\"", "\\\"")
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("accept", "application/json")
                .header("api-key", apiKey)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Email successfully sent via Brevo HTTP API to: " + toEmail);
            } else {
                throw new RuntimeException("Brevo API returned error status: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to send email via Brevo to " + toEmail + " due to: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void sendEmailViaResend(String toEmail, String subject, String body, String apiKey) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // Format JSON payload
            String jsonPayload = String.format(
                "{\"from\":\"onboarding@resend.dev\"," +
                "\"to\":[\"%s\"]," +
                "\"subject\":\"%s\"," +
                "\"html\":\"%s\"}",
                toEmail,
                subject.replace("\"", "\\\""),
                body.replace("\n", "<br>").replace("\"", "\\\"")
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Email successfully sent via Resend HTTP API to: " + toEmail);
            } else {
                throw new RuntimeException("Resend API returned error status: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to send email via Resend to " + toEmail + " due to: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void sendEmailViaSendGrid(String toEmail, String subject, String body, String apiKey) {
        String senderEmail = System.getenv("SMTP_USERNAME");
        if (senderEmail == null) {
            senderEmail = System.getProperty("SMTP_USERNAME");
        }
        if (senderEmail == null || senderEmail.trim().isEmpty()) {
            senderEmail = "himanshi.kh.2004@gmail.com"; // Default fallback
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // Format JSON payload for SendGrid v3 Mail Send
            String jsonPayload = String.format(
                "{\"personalizations\":[{\"to\":[{\"email\":\"%s\"}]}],\"from\":{\"email\":\"%s\",\"name\":\"Job Tracker\"},\"subject\":\"%s\",\"content\":[{\"type\":\"text/html\",\"value\":\"%s\"}]}",
                toEmail,
                senderEmail,
                subject.replace("\"", "\\\""),
                body.replace("\n", "<br>").replace("\"", "\\\"")
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Email successfully sent via SendGrid HTTP API to: " + toEmail);
            } else {
                throw new RuntimeException("SendGrid API returned error status: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to send email via SendGrid to " + toEmail + " due to: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void printMockEmail(String toEmail, String subject, String body, String mode) {
        System.out.println("==================================================");
        System.out.println("[MOCK EMAIL ALERT - " + mode + "]");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: " + subject);
        System.out.println("Body:\n" + body);
        System.out.println("==================================================");
    }
}
