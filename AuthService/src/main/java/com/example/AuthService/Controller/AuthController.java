package com.example.AuthService.Controller;

import com.example.AuthService.Model.MyUser;
import com.example.AuthService.Model.MyUserRepository;
import com.example.AuthService.Services.EmailService;
import com.example.AuthService.Services.JwtUtil;
import com.example.AuthService.Services.OtpService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.example.AuthService.Model.Role.*;

@RestController
public class AuthController {

    @Autowired private MyUserRepository repository;
    @Autowired private OtpService otpService;
    @Autowired private EmailService emailService;
    @Autowired private JwtUtil util;

    // ── Register ──────────────────────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<String> register(
            @RequestHeader String username,
            @RequestHeader String email,
            @RequestHeader String password,
            HttpServletResponse response) {

        Optional<MyUser> byUsername = repository.findByUsername(username);
        Optional<MyUser> byEmail    = repository.findByEmail(email);

        if (byUsername.isPresent() || byEmail.isPresent()) {
            return ResponseEntity.badRequest().body("Username or email already in use");
        }

        MyUser myUser = new MyUser();
        myUser.setEmail(email);
        myUser.setPassword(password);
        myUser.setUsername(username);
        myUser.setRole(USER);
        myUser.setEnabled(false);

        repository.save(myUser);

        String otp = otpService.generateOtp(email);
        emailService.sendOtpEmail(email, otp);

        return ResponseEntity.ok("Check your email for the OTP");
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────
    @PostMapping("/verifyotp")
    public ResponseEntity<String> verify(
            @RequestHeader String email,
            @RequestHeader String otp) {

        if (otpService.validateOtp(email, otp)) {
            Optional<MyUser> userOpt = repository.findByEmail(email);
            if (userOpt.isEmpty()) return ResponseEntity.badRequest().body("User not found");
            MyUser user = userOpt.get();
            user.setEnabled(true);
            repository.save(user); // Bug fix: was missing save() call
            return ResponseEntity.ok("Email verified successfully");
        }
        return ResponseEntity.badRequest().body("Invalid OTP or email");
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestHeader String username,
            @RequestHeader String password) {

        Optional<MyUser> userOpt = repository.findByUsername(username);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        MyUser user = userOpt.get();

        if (!user.getPassword().equals(password)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        if (!user.isEnabled()) {
            return ResponseEntity.status(403).body(Map.of("error", "Account not verified. Please check your email for the OTP."));
        }

        String token = util.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());

        return ResponseEntity.ok(response);
    }

    // ── Get current user (from token) ─────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authorization) {
        try {
            String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
            MyUser user = util.extractUser(token);

            Map<String, Object> response = new HashMap<>();
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            response.put("role", user.getRole().name());
            response.put("enabled", user.isEnabled());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }

    // ── Forgot Password ───────────────────────────────────────────────────────
    @PostMapping("/forgotpassword")
    public ResponseEntity<String> forgotPassword(@RequestHeader String email) {
        Optional<MyUser> userOpt = repository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("No account found with that email");
        }
        emailService.sendForgotPasswordEmail(email, userOpt.get().getPassword());
        return ResponseEntity.ok("Password sent to your email");
    }

    // ── Extract username from token (internal Feign use) ─────────────────────
    @PostMapping("/extractuser")
    public String username(@RequestHeader String token) {
        // Strip Bearer prefix if present (Feign may pass full Authorization header value)
        String cleaned = token.startsWith("Bearer ") ? token.substring(7) : token;
        return util.extractUser(cleaned).getUsername();
    }
}
