package com.jobtracker.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.jobtracker.service.MailService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MailService mailService;

    // Email validation regex
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    // ✅ SEND OTP
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String purpose = request.get("purpose"); // "signup" or "reset"

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            email = email.trim();

            if (!isValidEmail(email)) {
                return ResponseEntity.badRequest().body("Invalid email address format");
            }

            if ("signup".equalsIgnoreCase(purpose)) {
                if (userRepository.findByEmail(email).isPresent()) {
                    return ResponseEntity.badRequest().body("Email is already registered");
                }
            } else if ("reset".equalsIgnoreCase(purpose)) {
                if (userRepository.findByEmail(email).isEmpty()) {
                    return ResponseEntity.badRequest().body("No account found associated with this email");
                }
            } else {
                return ResponseEntity.badRequest().body("Invalid purpose specified");
            }

            // Generate 6-digit numeric OTP
            String otp = String.format("%06d", new Random().nextInt(1000000));

            // Save OTP to DB (cleanup old first)
            otpVerificationRepository.deleteByEmail(email);
            OtpVerification verification = new OtpVerification();
            verification.setEmail(email);
            verification.setOtp(otp);
            verification.setPurpose(purpose.toLowerCase());
            verification.setExpiryTime(LocalDateTime.now().plusMinutes(10));
            otpVerificationRepository.save(verification);

            // Send Email
            String username = null;
            if ("reset".equalsIgnoreCase(purpose)) {
                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    username = userOpt.get().getUsername();
                }
            }
            mailService.sendOtpEmail(email, username, otp, purpose);

            return ResponseEntity.ok(Map.of("message", "OTP sent successfully to " + email));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing OTP: " + e.getMessage());
        }
    }

    // ✅ REGISTER WITH OTP
    @PostMapping("/register-with-otp")
    public ResponseEntity<?> registerWithOtp(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String email = request.get("email");
            String password = request.get("password");
            String otp = request.get("otp");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Username is required");
            }
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            if (password == null || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required");
            }
            if (otp == null || otp.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Verification OTP is required");
            }

            email = email.trim();
            username = username.trim();
            otp = otp.trim();

            if (!isValidEmail(email)) {
                return ResponseEntity.badRequest().body("Invalid email address format");
            }

            if (userRepository.findByUsername(username).isPresent()) {
                return ResponseEntity.badRequest().body("Username is already taken");
            }

            if (userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body("Email is already registered");
            }

            // Verify OTP
            Optional<OtpVerification> verificationOpt = 
                    otpVerificationRepository.findTopByEmailAndPurposeOrderByExpiryTimeDesc(email, "signup");

            if (verificationOpt.isEmpty() || !verificationOpt.get().getOtp().equals(otp)) {
                return ResponseEntity.badRequest().body("Invalid verification code");
            }

            if (LocalDateTime.now().isAfter(verificationOpt.get().getExpiryTime())) {
                return ResponseEntity.badRequest().body("Verification code has expired");
            }

            // Save User
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);

            // Cleanup OTP
            otpVerificationRepository.deleteByEmail(email);

            return ResponseEntity.ok("User registered successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // ✅ RESET PASSWORD WITH OTP
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otp = request.get("otp");
            String newPassword = request.get("newPassword");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            if (otp == null || otp.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Verification OTP is required");
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("New password is required");
            }

            email = email.trim();
            otp = otp.trim();

            if (!isValidEmail(email)) {
                return ResponseEntity.badRequest().body("Invalid email address format");
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("No account associated with this email");
            }

            // Verify OTP
            Optional<OtpVerification> verificationOpt = 
                    otpVerificationRepository.findTopByEmailAndPurposeOrderByExpiryTimeDesc(email, "reset");

            if (verificationOpt.isEmpty() || !verificationOpt.get().getOtp().equals(otp)) {
                return ResponseEntity.badRequest().body("Invalid verification code");
            }

            if (LocalDateTime.now().isAfter(verificationOpt.get().getExpiryTime())) {
                return ResponseEntity.badRequest().body("Verification code has expired");
            }

            // Update Password
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            // Cleanup OTP
            otpVerificationRepository.deleteByEmail(email);

            return ResponseEntity.ok("Password reset successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // ✅ LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {

        Optional<User> existingUserOpt = userRepository.findByUsername(user.getUsername());

        if (existingUserOpt.isEmpty()) {
            // Check if user entered email instead of username
            existingUserOpt = userRepository.findByEmail(user.getUsername());
            if (existingUserOpt.isEmpty()) {
                return ResponseEntity.status(401).body("Account not found");
            }
        }

        User existingUser = existingUserOpt.get();

        if (!passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
            return ResponseEntity.status(401).body("Invalid password");
        }

        String token = JwtUtil.generateToken(existingUser.getUsername());

        // ✅ FINAL RESPONSE
        return ResponseEntity.ok(
                Map.of(
                        "token", token,
                        "userId", existingUser.getId(),
                        "username", existingUser.getUsername()
                )
        );
    }
}