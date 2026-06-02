package com.socdss.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/status")
    public AuthStatus status(HttpSession session) {
        return authService.status(session);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthStatus> login(@RequestBody LoginRequest request, HttpSession session) {
        return ResponseEntity.ok(authService.login(request, session));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthStatus> changePassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
        return ResponseEntity.ok(authService.changePassword(request, session));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.noContent().build();
    }
}
