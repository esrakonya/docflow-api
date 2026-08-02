package io.docflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class DocflowApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(DocflowApiApplication.class, args);
	}
}
