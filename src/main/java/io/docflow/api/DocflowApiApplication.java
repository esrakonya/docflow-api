package io.docflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class DocflowApiApplication {

	public static void main(String[] args) {

		SpringApplication.run(DocflowApiApplication.class, args);
	}

}
