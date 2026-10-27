package com.invoice.platform.boundary;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    record HealthResponse(String status, String application, String version, Instant timestamp) {
        static HealthResponse ok() {
            return new HealthResponse("UP", "ai-invoice-gst-platform", "0.0.1-SNAPSHOT", Instant.now());
        }
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.ok());
    }
}
