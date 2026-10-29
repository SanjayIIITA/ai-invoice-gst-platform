package com.invoice.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiInvoiceGstPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiInvoiceGstPlatformApplication.class, args);
	}

}
