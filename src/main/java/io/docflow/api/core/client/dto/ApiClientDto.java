package io.docflow.api.core.client.dto;

import io.docflow.api.core.client.entity.ClientStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiClientDto implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private UUID id;
    private String companyName;
    private String apiKeyHash;
    private ClientStatus status;
    private Integer remainingQuota;
}
