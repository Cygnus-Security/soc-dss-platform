package com.socdss.auth;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
