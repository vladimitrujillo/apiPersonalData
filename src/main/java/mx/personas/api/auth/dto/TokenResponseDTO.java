package mx.personas.api.auth.dto;

import java.time.OffsetDateTime;

public record TokenResponseDTO(
        String accessToken,
        String refreshToken,
        OffsetDateTime expiraEn
) {
}
