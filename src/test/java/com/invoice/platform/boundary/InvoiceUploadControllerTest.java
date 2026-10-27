package com.invoice.platform.boundary;

import com.invoice.platform.control.InvoiceService;
import com.invoice.platform.control.InvoiceUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvoiceUploadControllerTest {

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private InvoiceUploadService uploadService;

    @InjectMocks
    private InvoiceController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void upload_returnsBadRequestForEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_returnsBadRequestForNonPdfFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.txt", "text/plain", "some text".getBytes());

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_returnsCreatedForValidPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "pdf content".getBytes());

        when(uploadService.upload(any(), any())).thenReturn(null);

        mockMvc.perform(multipart("/api/v1/invoices/upload").file(file))
                .andExpect(status().isCreated());
    }
}
