package com.example.AuthService.Services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends OTP email asynchronously so a SMTP failure doesn't block/crash registration.
     */
    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setTo(toEmail);
            h.setSubject("🔐 Your StreamFlix OTP Code");
            h.setText(
                "Hi there!\n\n" +
                "Your One-Time Password (OTP) to verify your StreamFlix account is:\n\n" +
                "  ➡  " + otp + "\n\n" +
                "This OTP will expire in 10 minutes.\n\n" +
                "If you didn't request this, you can safely ignore this email.\n\n" +
                "— The StreamFlix Team"
            );
            mailSender.send(msg);
            System.out.println("✅ OTP sent to " + toEmail);
        } catch (Exception e) {
            // Log SMTP errors but don't crash — user can still verify if they see OTP in console
            System.err.println("⚠️ Failed to send OTP email to " + toEmail + ": " + e.getMessage());
            System.out.println("📋 OTP for " + toEmail + " is: " + otp + " (check console for dev testing)");
        }
    }

    /**
     * Sends a forgot-password email asynchronously.
     */
    @Async
    public void sendForgotPasswordEmail(String toEmail, String password) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setTo(toEmail);
            h.setSubject("🔑 Your StreamFlix Account Password");
            h.setText(
                "Hi there!\n\n" +
                "You requested your account password.\n\n" +
                "  🔐 Password: " + password + "\n\n" +
                "For security, please change it after logging in.\n\n" +
                "— The StreamFlix Team"
            );
            mailSender.send(msg);
            System.out.println("✅ Password recovery email sent to " + toEmail);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to send recovery email to " + toEmail + ": " + e.getMessage());
        }
    }
}
