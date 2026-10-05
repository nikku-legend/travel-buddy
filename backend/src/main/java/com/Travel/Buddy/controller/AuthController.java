package com.Travel.Buddy.controller;

import com.Travel.Buddy.dto.auth.AuthResponse;
import com.Travel.Buddy.dto.auth.LoginRequest;
import com.Travel.Buddy.dto.auth.LogoutRequest;
import com.Travel.Buddy.dto.auth.RefreshTokenRequest;
import com.Travel.Buddy.dto.auth.RegisterRequest;
import com.Travel.Buddy.service.auth.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService
    ) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        authService.register(request)
                );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody
            RefreshTokenRequest request
    ) {

        return ResponseEntity.ok(
                authService.refresh(request)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody
            LogoutRequest request
    ) {

        authService.logout(
                request.refreshToken()
        );

        return ResponseEntity.noContent().build();
    }
}