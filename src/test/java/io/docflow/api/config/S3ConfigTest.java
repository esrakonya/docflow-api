package io.docflow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for: local storage used to require MinIO configuration even though
 * it never used it. S3Config's beans must only be created when storage.type=minio.
 * Uses ApplicationContextRunner (not @SpringBootTest) so this runs in milliseconds
 * with no database, Kafka, Redis, or Testcontainers involved.
 */
class S3ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3Config.class);

    @Test
    void storageTypeLocal_doesNotCreateAnyS3Beans_evenWithoutMinioProperties() {
        contextRunner
                .withPropertyValues("storage.type=local")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context).doesNotHaveBean("createBucketIfNotExists");
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void storageTypeMinio_withValidProperties_createsS3Client() {
        contextRunner
                .withPropertyValues(
                        "storage.type=minio",
                        "storage.minio.endpoint=http://localhost:9000",
                        "storage.minio.access-key=test-access",
                        "storage.minio.secret-key=test-secret",
                        "storage.minio.bucket-name=test-bucket"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(CommandLineRunner.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void storageTypeMinio_missingMinioProperties_failsToStart() {
        contextRunner
                .withPropertyValues("storage.type=minio")
                .run(context -> assertThat(context).hasFailed());
    }
}
