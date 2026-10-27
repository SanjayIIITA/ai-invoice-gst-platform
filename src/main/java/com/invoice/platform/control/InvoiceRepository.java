package com.invoice.platform.control;

import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsByInvoiceNumberAndOrganizationId(String invoiceNumber, Long orgId);
    boolean existsByInvoiceNumberAndSupplierGstinAndIdNotAndOrganizationId(String invoiceNumber, String supplierGstin, Long id, Long orgId);
    List<Invoice> findByStatus(InvoiceStatus status);
    List<Invoice> findByOrganizationId(Long orgId);
    List<Invoice> findByOrganizationIdAndStatus(Long orgId, InvoiceStatus status);
    Optional<Invoice> findByIdAndOrganizationId(Long id, Long orgId);
    boolean existsByIdAndOrganizationId(Long id, Long orgId);
}
