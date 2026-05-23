package com.spectrumai.backend.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code POST /v1/searches}.
 *
 * <p>{@code brand}/{@code model}/{@code trim} restringem o conjunto de
 * caracteres aceitos para evitar payloads malformados, injection ou poluicao
 * de prompt enviado ao LLM.</p>
 */
public record SearchRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^[A-Za-z0-9\\u00C0-\\u017F\\s\\-./&()]+$",
                message = "marca contem caracteres invalidos")
        String brand,

        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^[A-Za-z0-9\\u00C0-\\u017F\\s\\-./&()]+$",
                message = "modelo contem caracteres invalidos")
        String model,

        @Size(max = 120)
        @Pattern(regexp = "^[A-Za-z0-9\\u00C0-\\u017F\\s\\-./&()]*$",
                message = "versao contem caracteres invalidos")
        String trim,

        @Min(1990) @Max(2100) Integer year,

        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 60) String> categories,

        UUID sessionId
) {}
