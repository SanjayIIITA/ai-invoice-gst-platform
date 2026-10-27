package com.invoice.platform.boundary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OpenApiController {

    @GetMapping("/v3/api-docs")
    public Map<String, Object> apiDocs() {
        return Map.of(
            "openapi", "3.0.3",
            "info", Map.of(
                "title", "AI Invoice GST Platform",
                "description", "Upload GST invoice PDFs — AI extracts fields, rules validate, humans approve.",
                "version", "1.0.0"
            ),
            "components", Map.of(
                "securitySchemes", Map.of(
                    "Bearer", Map.of(
                        "type", "http",
                        "scheme", "bearer",
                        "bearerFormat", "JWT"
                    )
                ),
                "schemas", Map.of(
                    "RegisterRequest", Map.of("type", "object", "properties", Map.of(
                        "email",            Map.of("type", "string", "example", "admin@testorg.com"),
                        "password",         Map.of("type", "string", "example", "password123"),
                        "fullName",         Map.of("type", "string", "example", "Admin User"),
                        "organizationName", Map.of("type", "string", "example", "Test Org")
                    )),
                    "LoginRequest", Map.of("type", "object", "properties", Map.of(
                        "email",    Map.of("type", "string", "example", "admin@testorg.com"),
                        "password", Map.of("type", "string", "example", "password123")
                    )),
                    "RejectRequest", Map.of("type", "object", "properties", Map.of(
                        "reason", Map.of("type", "string", "example", "Tax calculation mismatch")
                    )),
                    "UpdateFindingRequest", Map.of("type", "object", "properties", Map.of(
                        "status", Map.of("type", "string", "enum", java.util.List.of("OPEN", "ACKNOWLEDGED", "RESOLVED", "IGNORED"))
                    ))
                )
            ),
            "security", java.util.List.of(Map.of("Bearer", java.util.List.of())),
            "paths", buildPaths()
        );
    }

    private Map<String, Object> buildPaths() {
        return Map.ofEntries(
            // Auth
            Map.entry("/api/v1/auth/register", Map.of(
                "post", endpoint("Auth", "Register", "Create a new organization and admin user",
                    body("RegisterRequest"), response201())
            )),
            Map.entry("/api/v1/auth/login", Map.of(
                "post", endpoint("Auth", "Login", "Login and receive a JWT token",
                    body("LoginRequest"), response200())
            )),
            // Invoices
            Map.entry("/api/v1/invoices/upload", Map.of(
                "post", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "uploadInvoice",
                    "summary", "Upload a PDF invoice",
                    "requestBody", Map.of(
                        "required", true,
                        "content", Map.of(
                            "multipart/form-data", Map.of(
                                "schema", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                        "file", Map.of("type", "string", "format", "binary")
                                    )
                                )
                            )
                        )
                    ),
                    "responses", response201()
                )
            )),
            Map.entry("/api/v1/invoices", Map.of(
                "get", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "listInvoices",
                    "summary", "List all invoices, optionally filter by status",
                    "parameters", java.util.List.of(Map.of(
                        "name", "status", "in", "query", "required", false,
                        "schema", Map.of("type", "string",
                            "enum", java.util.List.of("UPLOADED","EXTRACTING","EXTRACTED","FAILED","VALIDATING","VALIDATED","APPROVED","REJECTED"))
                    )),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}", Map.of(
                "get", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "getInvoice",
                    "summary", "Get invoice by ID",
                    "parameters", java.util.List.of(pathParam("id")),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/extract", Map.of(
                "post", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "extractInvoice",
                    "summary", "Trigger AI extraction — reads PDF and fills invoice fields",
                    "parameters", java.util.List.of(pathParam("id")),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/validate", Map.of(
                "post", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "validateInvoice",
                    "summary", "Run 6 validation rules against extracted invoice data",
                    "parameters", java.util.List.of(pathParam("id")),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/findings", Map.of(
                "get", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "getFindings",
                    "summary", "List all validation findings for an invoice",
                    "parameters", java.util.List.of(pathParam("id")),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/findings/{findingId}", Map.of(
                "patch", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "updateFinding",
                    "summary", "Update finding status (OPEN, ACKNOWLEDGED, RESOLVED, IGNORED)",
                    "parameters", java.util.List.of(pathParam("id"), pathParam("findingId")),
                    "requestBody", body("UpdateFindingRequest"),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/explain", Map.of(
                "get", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "explainFindings",
                    "summary", "AI explains validation findings in plain English",
                    "parameters", java.util.List.of(pathParam("id")),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/approve", Map.of(
                "post", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "approveInvoice",
                    "summary", "Approve invoice — requires ADMIN or REVIEWER role",
                    "parameters", java.util.List.of(pathParam("id")),
                    "responses", response200()
                )
            )),
            Map.entry("/api/v1/invoices/{id}/reject", Map.of(
                "post", Map.of(
                    "tags", java.util.List.of("Invoices"),
                    "operationId", "rejectInvoice",
                    "summary", "Reject invoice with a reason — requires ADMIN or REVIEWER role",
                    "parameters", java.util.List.of(pathParam("id")),
                    "requestBody", body("RejectRequest"),
                    "responses", response200()
                )
            )),
            // Reports
            Map.entry("/api/v1/reports/summary", Map.of(
                "get", Map.of(
                    "tags", java.util.List.of("Reports"),
                    "operationId", "getSummary",
                    "summary", "Get invoice counts and totals grouped by status",
                    "responses", response200()
                )
            ))
        );
    }

    private Map<String, Object> endpoint(String tag, String operationId, String summary,
                                          Map<String, Object> requestBody, Map<String, Object> responses) {
        return Map.of(
            "tags", java.util.List.of(tag),
            "operationId", operationId,
            "summary", summary,
            "requestBody", requestBody,
            "responses", responses
        );
    }

    private Map<String, Object> body(String schemaRef) {
        return Map.of(
            "required", true,
            "content", Map.of(
                "application/json", Map.of(
                    "schema", Map.of("$ref", "#/components/schemas/" + schemaRef)
                )
            )
        );
    }

    private Map<String, Object> pathParam(String name) {
        return Map.of("name", name, "in", "path", "required", true,
                "schema", Map.of("type", "integer", "format", "int64"));
    }

    private Map<String, Object> response200() {
        return Map.of("200", Map.of("description", "Success"));
    }

    private Map<String, Object> response201() {
        return Map.of("201", Map.of("description", "Created"));
    }
}
