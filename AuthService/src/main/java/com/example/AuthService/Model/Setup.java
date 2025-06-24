package com.example.AuthService.Model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import static com.example.AuthService.Model.Role.*;

@Component
public class Setup implements CommandLineRunner {
    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.email}")
    private String adminEmail;
    @Autowired
    private MyUserRepository repository;


    @Override
    public void run(String... args) throws Exception {

        MyUser user=new MyUser();
        user.setEmail(adminEmail);
        user.setUsername(adminUsername);
        user.setPassword(adminPassword);
        user.setEnabled(true);
        user.setRole(ADMIN);
        repository.save(user);
    }
}
