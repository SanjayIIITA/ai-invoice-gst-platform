package com.invoice.platform.control;

import com.invoice.platform.boundary.InvoiceExtractionResult;
import com.invoice.platform.entity.ValidationFinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvoiceAiService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceAiService.class);

    private final ChatClient chatClient;

    public InvoiceAiService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public InvoiceExtractionResult extractInvoiceFields(String rawText) {
        String prompt = """
                You are an invoice data extraction assistant.
                Extract the following fields from the invoice text below.
                Return ONLY a valid JSON object with these exact keys:
                invoiceNumber, supplierName, supplierGstin, invoiceDate (YYYY-MM-DD),
                taxableAmount, taxAmount, totalAmount.
                If a field is not found, use null.
                Do not include any explanation or markdown, only the JSON object.
                
                Invoice text:
                %s
                """.formatted(rawText);

        String rawResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("AI raw response: {}", rawResponse);

        InvoiceExtractionResult result = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(InvoiceExtractionResult.class);

        log.info("AI extracted result: {}", result);
        return result;
    }

    public String explainFindings(List<ValidationFinding> findings) {
        if (findings.isEmpty()) {
            return "No validation issues were found for this invoice.";
        }

        String findingsSummary = findings.stream()
                .map(f -> "- [%s] %s: %s".formatted(f.getSeverity(), f.getRuleCode(), f.getMessage()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a finance assistant helping reviewers understand invoice validation issues.
                Explain the following validation findings in simple plain English.
                Be concise, clear and actionable. Do not use technical jargon.
                
                Findings:
                %s
                """.formatted(findingsSummary);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
