package com.invoice.platform.boundary;

public record AuthResponse(String token, String email, String fullName, String role, Long orgId) {}
