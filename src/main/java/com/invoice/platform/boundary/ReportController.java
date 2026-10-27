package com.invoice.platform.boundary;

import com.invoice.platform.control.ReportService;
import com.invoice.platform.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> summary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(reportService.summary(user.getOrganization().getId()));
    }
}
