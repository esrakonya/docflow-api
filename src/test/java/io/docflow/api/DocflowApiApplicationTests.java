package io.docflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnableScheduling
class DocflowApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
