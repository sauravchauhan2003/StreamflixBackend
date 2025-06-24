package com.example.AuthService.Services;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
@Service
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long EXPIRY_DURATION_MINUTES = 10;
    private static final SecureRandom random = new SecureRandom();
    private static final int CLEANUP_THRESHOLD = 1000;

    private final ConcurrentMap<String, OtpEntry> otpStorage = new ConcurrentHashMap<>();
    private int generationCount = 0;

    public synchronized String generateOtp(String key) {
        String otp = generateRandomOtp();
        otpStorage.put(key, new OtpEntry(otp, Instant.now()));

        generationCount++;
        if (generationCount >= CLEANUP_THRESHOLD) {
            removeExpiredOtps();
            generationCount = 0;
        }

        return otp;
    }

    public boolean validateOtp(String key, String inputOtp) {
        OtpEntry entry = otpStorage.get(key);
        if (entry == null) return false;

        if (isExpired(entry)) {
            otpStorage.remove(key);
            return false;
        }

        if (entry.otp.equals(inputOtp)) {
            otpStorage.remove(key);
            return true;
        }

        return false;
    }

    private void removeExpiredOtps() {
        Instant now = Instant.now();
        for (Map.Entry<String, OtpEntry> entry : otpStorage.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                otpStorage.remove(entry.getKey());
            }
        }
    }

    private boolean isExpired(OtpEntry entry) {
        return isExpired(entry, Instant.now());
    }

    private boolean isExpired(OtpEntry entry, Instant now) {
        return now.isAfter(entry.timestamp.plusSeconds(EXPIRY_DURATION_MINUTES * 60));
    }

    private String generateRandomOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    private static class OtpEntry {
        String otp;
        Instant timestamp;

        OtpEntry(String otp, Instant timestamp) {
            this.otp = otp;
            this.timestamp = timestamp;
        }
    }
}