package com.example.AuthService.Services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your OTP Code");
        message.setText("Your One-Time Password (OTP) is: " + otp + "\n\nIt will expire in 10 minutes.");

        mailSender.send(message);
        System.out.println("✅ OTP sent to " + toEmail);
    }
    public void sendForgotPasswordEmail(String toEmail,String password){
        SimpleMailMessage message=new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your Forgotten Password - Account Recovery");
        message.setText("Hi [User's Name],\n" +
                "\n" +
                "As requested, here is the password associated with your account:\n" +
                "\n" +
                "\uD83D\uDD10 Password:"+password);
        System.out.println("✅ Password sent to " + toEmail);
    }
}
