package io.docflow.api.core.storage.service;

import io.docflow.api.infrastructure.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Plain Mockito unit test: bucketName is now constructor-injected instead of a @Value
 * field, so S3Client can be mocked and the whole class exercised without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    private static final String BUCKET = "docflow-test-bucket";

    @Mock
    private S3Client s3Client;

    private S3StorageService service;

    @BeforeEach
    void setUp() {
        service = new S3StorageService(s3Client, BUCKET);
    }

    @Test
    void store_putsObjectInConfiguredBucket() {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "content".getBytes());

        String key = service.store(file);

        assertThat(key).endsWith("_invoice.pdf");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    void store_wrapsSdkFailureInStorageException() {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", "content".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> service.store(file)).isInstanceOf(StorageException.class);
    }

    @Test
    void fetch_returnsBytesFromConfiguredBucket() {
        ResponseBytes<GetObjectResponse> response =
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "content".getBytes());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(response);

        byte[] result = service.fetch("some-key.pdf");

        assertThat(result).isEqualTo("content".getBytes());
    }

    @SuppressWarnings("unchecked")
    @Test
    void cleanup_deletesOnlyObjectsOlderThanRetention() {
        Instant old = Instant.now().minus(40, ChronoUnit.DAYS);
        Instant recent = Instant.now().minus(2, ChronoUnit.DAYS);

        S3Object oldObject = S3Object.builder().key("old.pdf").lastModified(old).build();
        S3Object recentObject = S3Object.builder().key("recent.pdf").lastModified(recent).build();

        when(s3Client.listObjectsV2(any(Consumer.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(List.of(oldObject, recentObject)).build());

        int deletedCount = service.cleanup(30);

        assertThat(deletedCount).isEqualTo(1);
        verify(s3Client, times(1)).deleteObject(any(Consumer.class));
    }
}
