package io.docflow.api.core.client.dto;

import io.docflow.api.core.client.entity.ClientStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiClientDto implements Serializable {
    @Serial private static final long serialVersionUID = 5L;

    private UUID id;
    private String companyName;
    private ClientStatus status;
    private Integer monthlyQuota;
    private Integer rateLimitPerMin;
    private String planName;
    private OffsetDateTime expiresAt;
    private String stripeCustomerId;
}
