package com.example.restaurant.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushDeviceRegistrationRequest(
        @NotBlank @Size(max = 512) String installationId,
        @NotBlank @Size(max = 30) String channel,
        @Size(max = 500) String userAgent
) {
}
