package com.invoice.platform.boundary;

import com.invoice.platform.entity.FindingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFindingRequest(@NotNull FindingStatus status) {}
