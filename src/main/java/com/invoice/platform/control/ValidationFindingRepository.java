package com.invoice.platform.control;

import com.invoice.platform.entity.FindingStatus;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ValidationFindingRepository extends JpaRepository<ValidationFinding, Long> {

    @Query("""
            SELECT f FROM ValidationFinding f
            WHERE f.invoice.id = :invoiceId
            ORDER BY
              CASE f.severity
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH'     THEN 2
                WHEN 'MEDIUM'   THEN 3
                WHEN 'LOW'      THEN 4
                WHEN 'INFO'     THEN 5
              END ASC,
              f.createdAt ASC
            """)
    List<ValidationFinding> findByInvoiceIdOrdered(@Param("invoiceId") Long invoiceId);

    @Query("""
            SELECT f FROM ValidationFinding f
            WHERE f.invoice.id = :invoiceId
              AND f.status NOT IN :excludedStatuses
            ORDER BY
              CASE f.severity
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH'     THEN 2
                WHEN 'MEDIUM'   THEN 3
                WHEN 'LOW'      THEN 4
                WHEN 'INFO'     THEN 5
              END ASC,
              f.createdAt ASC
            """)
    List<ValidationFinding> findActiveByInvoiceIdOrdered(@Param("invoiceId") Long invoiceId,
                                                         @Param("excludedStatuses") List<FindingStatus> excludedStatuses);

    Optional<ValidationFinding> findByInvoiceIdAndId(Long invoiceId, Long findingId);
}
