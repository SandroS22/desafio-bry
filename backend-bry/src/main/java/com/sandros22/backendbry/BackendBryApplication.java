package com.sandros22.backendbry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class BackendBryApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendBryApplication.class, args);
	}

}
