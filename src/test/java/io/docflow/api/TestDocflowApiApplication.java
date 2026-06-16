package io.docflow.api;

import org.springframework.boot.SpringApplication;

public class TestDocflowApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(DocflowApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
