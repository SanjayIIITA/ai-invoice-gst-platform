package com.invoice.platform.boundary;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(@NotBlank String reason) {}
