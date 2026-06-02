package com.socdss.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {
    private final AppUserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(AppUserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public AuthStatus status(HttpSession session) {
        Long userId = (Long) session.getAttribute(AuthSession.USER_ID);
        if (userId == null) {
            return new AuthStatus(false, null, false);
        }
        return userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .map(user -> new AuthStatus(true, user.getUsername(), Boolean.TRUE.equals(user.getMustChangePassword())))
                .orElseGet(() -> new AuthStatus(false, null, false));
    }

    public AuthStatus login(LoginRequest request, HttpSession session) {
        AppUser user = userRepository.findByUsername(request.username())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                .filter(candidate -> passwordHasher.verify(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        session.setAttribute(AuthSession.USER_ID, user.getId());
        session.setAttribute(AuthSession.USERNAME, user.getUsername());
        return new AuthStatus(true, user.getUsername(), Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    public AuthStatus changePassword(ChangePasswordRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute(AuthSession.USER_ID);
        if (userId == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new IllegalArgumentException("New password must have at least 8 characters");
        }
        AppUser user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordHasher.verify(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setMustChangePassword(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return new AuthStatus(true, user.getUsername(), false);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}
