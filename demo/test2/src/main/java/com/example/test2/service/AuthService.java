package com.example.test2.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Value("${app.auth.username:admin}")
    private String configuredUsername;

    @Value("${app.auth.password:password}")
    private String configuredPassword;

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        return username.equals(configuredUsername) && password.equals(configuredPassword);
    }
}
