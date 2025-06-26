package com.example.AuthService.Controller;

import com.example.AuthService.Model.MyUser;
import com.example.AuthService.Model.MyUserRepository;
import com.example.AuthService.Services.EmailService;
import com.example.AuthService.Services.JwtUtil;
import com.example.AuthService.Services.OtpService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.example.AuthService.Model.Role.*;

@RestController
public class AuthController {
    @Autowired
    private MyUserRepository repository;
    @Autowired
    private OtpService otpService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private JwtUtil util;
    @PostMapping("/register")
    public String register(@RequestHeader String username, @RequestHeader String email, @RequestHeader String  password, HttpServletResponse response){
       Optional<MyUser> a=repository.findByUsername(username);
       Optional<MyUser> b=repository.findByEmail(email);
       if(a.isPresent()||b.isPresent()){
           response.setStatus(400);
           return "Username or email already in use";
       }
       else{
           response.setStatus(200);
           MyUser myUser=new MyUser();
           myUser.setEmail(email);
           myUser.setPassword(password);
           myUser.setUsername(username);
           myUser.setRole(USER);
           myUser.setEnabled(false);
           String otp=otpService.generateOtp(email);
           repository.save(myUser);
           emailService.sendOtpEmail(email,otp);
           return "Check your email for otp";
       }
    }
    @PostMapping("/verifyotp")
    public String verify(@RequestHeader String email,@RequestHeader String otp,HttpServletResponse response){
        if(otpService.validateOtp(email,otp)){
            repository.findByEmail(email).get().setEnabled(true);
            response.setStatus(200);
            return "Email verified";
        }
        else{
            response.setStatus(400);
            return "Invalid otp or email";
        }
    }
    @PostMapping("/forgotpassword")
    public String forgotpassword(@RequestHeader String email, HttpServletResponse response){
        if(repository.findByEmail(email).isPresent()){
            response.setStatus(200);
            String password=repository.findByEmail(email).get().getPassword();
            emailService.sendForgotPasswordEmail(email,password);
            return "Check your email for your password";
        }
        else{
            response.setStatus(400);
            return "Invalid email";
        }
    }
    @PostMapping("/extractuser")
    public String username(@RequestHeader String token){
        return util.extractUser(token).getUsername();
    }
}
