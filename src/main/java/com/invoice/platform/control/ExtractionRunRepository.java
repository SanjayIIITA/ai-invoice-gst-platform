package com.invoice.platform.control;

import com.invoice.platform.entity.ExtractionRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, Long> {
    List<ExtractionRun> findByInvoiceIdOrderByStartedAtDesc(Long invoiceId);
}
