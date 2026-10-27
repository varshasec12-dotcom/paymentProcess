package com.bank.payments.controller;

import com.bank.payments.dto.RegisterRequest;
import com.bank.payments.model.Customer;
import com.bank.payments.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Login (POST /api/auth/login) and logout (POST /api/auth/logout) are handled by
// Spring Security filters configured in SecurityConfig, not by methods here.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Pages call this first and send the token back in the given header on POST requests
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
