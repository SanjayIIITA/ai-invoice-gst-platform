package com.invoice.platform.control;

import com.invoice.platform.boundary.ExplainResponse;
import com.invoice.platform.boundary.FindingResponse;
import com.invoice.platform.entity.FindingStatus;
import com.invoice.platform.entity.InvoiceNotFoundException;
import com.invoice.platform.entity.ValidationFinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ValidationService {

    private final InvoiceRepository invoiceRepository;
    private final ValidationEngine validationEngine;
    private final ValidationFindingRepository findingRepository;
    private final InvoiceAiService aiService;

    public ValidationService(InvoiceRepository invoiceRepository,
                             ValidationEngine validationEngine,
                             ValidationFindingRepository findingRepository,
                             InvoiceAiService aiService) {
        this.invoiceRepository = invoiceRepository;
        this.validationEngine = validationEngine;
        this.findingRepository = findingRepository;
        this.aiService = aiService;
    }

    @Transactional
    public List<FindingResponse> validate(Long invoiceId, Long orgId) {
        return invoiceRepository.findByIdAndOrganizationId(invoiceId, orgId)
                .map(invoice -> validationEngine.validate(invoice)
                        .stream().map(FindingResponse::from).toList())
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
    }

    public List<FindingResponse> findingsFor(Long invoiceId, Long orgId) {
        if (!invoiceRepository.existsByIdAndOrganizationId(invoiceId, orgId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        return findingRepository.findByInvoiceIdOrdered(invoiceId)
                .stream().map(FindingResponse::from).toList();
    }

    @Transactional
    public FindingResponse updateFinding(Long invoiceId, Long findingId, FindingStatus status, Long orgId) {
        if (!invoiceRepository.existsByIdAndOrganizationId(invoiceId, orgId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        ValidationFinding finding = findingRepository.findByInvoiceIdAndId(invoiceId, findingId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        finding.setStatus(status);
        return FindingResponse.from(findingRepository.save(finding));
    }

    public ExplainResponse explain(Long invoiceId, Long orgId) {
        if (!invoiceRepository.existsByIdAndOrganizationId(invoiceId, orgId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        List<ValidationFinding> findings = findingRepository
                .findActiveByInvoiceIdOrdered(
                        invoiceId, List.of(FindingStatus.RESOLVED, FindingStatus.IGNORED));
        String explanation = aiService.explainFindings(findings);
        return new ExplainResponse(invoiceId, explanation);
    }
}
