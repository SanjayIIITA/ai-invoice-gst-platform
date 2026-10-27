package com.invoice.platform.control.rules;

import com.invoice.platform.entity.FindingSeverity;
import com.invoice.platform.entity.Invoice;
import com.invoice.platform.entity.ValidationFinding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    // ── RequiredFieldsRule ────────────────────────────────────────────────────

    @Test
    void requiredFields_noFindingsWhenAllPresent() {
        Invoice invoice = invoiceWithRequiredFields();
        assertThat(new RequiredFieldsRule().validate(invoice)).isEmpty();
    }

    @Test
    void requiredFields_findsAllMissingFields() {
        List<ValidationFinding> findings = new RequiredFieldsRule().validate(new Invoice());
        assertThat(findings).hasSize(4);
        assertThat(findings).allMatch(f -> f.getSeverity() == FindingSeverity.HIGH);
    }

    // ── TaxCalculationRule ────────────────────────────────────────────────────

    @Test
    void taxCalculation_noFindingWhenCorrect() {
        Invoice invoice = new Invoice();
        invoice.setTaxableAmount(new BigDecimal("10000"));
        invoice.setTaxAmount(new BigDecimal("1800"));
        invoice.setTotalAmount(new BigDecimal("11800"));
        assertThat(new TaxCalculationRule().validate(invoice)).isEmpty();
    }

    @Test
    void taxCalculation_findingWhenMismatch() {
        Invoice invoice = new Invoice();
        invoice.setTaxableAmount(new BigDecimal("10000"));
        invoice.setTaxAmount(new BigDecimal("1800"));
        invoice.setTotalAmount(new BigDecimal("11900"));
        List<ValidationFinding> findings = new TaxCalculationRule().validate(invoice);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleCode()).isEqualTo("TAX_CALCULATION_MISMATCH");
        assertThat(findings.get(0).getSeverity()).isEqualTo(FindingSeverity.HIGH);
    }

    @Test
    void taxCalculation_noFindingWhenAmountsNull() {
        assertThat(new TaxCalculationRule().validate(new Invoice())).isEmpty();
    }

    // ── GstRateRule ───────────────────────────────────────────────────────────

    @Test
    void gstRate_noFindingForValidRate18() {
        Invoice invoice = new Invoice();
        invoice.setTaxableAmount(new BigDecimal("10000"));
        invoice.setTaxAmount(new BigDecimal("1800"));
        assertThat(new GstRateRule().validate(invoice)).isEmpty();
    }

    @Test
    void gstRate_findingForInvalidRate() {
        Invoice invoice = new Invoice();
        invoice.setTaxableAmount(new BigDecimal("10000"));
        invoice.setTaxAmount(new BigDecimal("1500"));
        List<ValidationFinding> findings = new GstRateRule().validate(invoice);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleCode()).isEqualTo("INVALID_GST_RATE");
        assertThat(findings.get(0).getSeverity()).isEqualTo(FindingSeverity.MEDIUM);
    }

    // ── GstinFormatRule ───────────────────────────────────────────────────────

    @Test
    void gstin_noFindingForValidGstin() {
        Invoice invoice = new Invoice();
        invoice.setSupplierGstin("29ABCDE1234F1Z5");
        assertThat(new GstinFormatRule().validate(invoice)).isEmpty();
    }

    @Test
    void gstin_findingForInvalidFormat() {
        Invoice invoice = new Invoice();
        invoice.setSupplierGstin("INVALID");
        List<ValidationFinding> findings = new GstinFormatRule().validate(invoice);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleCode()).isEqualTo("INVALID_GSTIN_FORMAT");
    }

    @Test
    void gstin_findingWhenMissing() {
        List<ValidationFinding> findings = new GstinFormatRule().validate(new Invoice());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleCode()).isEqualTo("MISSING_GSTIN");
    }

    // ── InvoiceDateRule ───────────────────────────────────────────────────────

    @Test
    void invoiceDate_noFindingForValidDate() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceDate(LocalDate.now().minusDays(10));
        assertThat(new InvoiceDateRule().validate(invoice)).isEmpty();
    }

    @Test
    void invoiceDate_findingForFutureDate() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceDate(LocalDate.now().plusDays(1));
        List<ValidationFinding> findings = new InvoiceDateRule().validate(invoice);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleCode()).isEqualTo("FUTURE_INVOICE_DATE");
        assertThat(findings.get(0).getSeverity()).isEqualTo(FindingSeverity.HIGH);
    }

    @Test
    void invoiceDate_findingForOldDate() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceDate(LocalDate.now().minusYears(2));
        List<ValidationFinding> findings = new InvoiceDateRule().validate(invoice);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleCode()).isEqualTo("INVOICE_DATE_TOO_OLD");
        assertThat(findings.get(0).getSeverity()).isEqualTo(FindingSeverity.LOW);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Invoice invoiceWithRequiredFields() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-001");
        invoice.setSupplierName("ABC Supplies");
        invoice.setInvoiceDate(LocalDate.now().minusDays(1));
        invoice.setTotalAmount(new BigDecimal("11800"));
        return invoice;
    }
}
