package mx.personas.api.auth.service;

import mx.personas.api.auth.dto.TokenResponseDTO;
import mx.personas.api.common.error.CredencialesInvalidasException;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.usuario.model.RefreshToken;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.RefreshTokenRepository;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@Transactional
public class AuthService {

    private static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public TokenResponseDTO login(String login, String password) {
        Usuario usuario = usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_GENERICO));

        if (!usuario.isActivo() || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException(MENSAJE_GENERICO);
        }

        return emitirTokens(usuario);
    }

    public TokenResponseDTO refresh(String refreshTokenValue) {
        String hash = jwtService.hashSha256(refreshTokenValue);
        RefreshToken tokenActual = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new CredencialesInvalidasException("Token de refresco inválido"));

        Usuario usuario = tokenActual.getUsuario();
        if (!tokenActual.estaVigente() || !usuario.isActivo()) {
            throw new CredencialesInvalidasException("Token de refresco inválido");
        }

        tokenActual.revocar();

        return emitirTokens(usuario);
    }

    private TokenResponseDTO emitirTokens(Usuario usuario) {
        String accessToken = jwtService.generarAccessToken(usuario.getLogin(), usuario.getRol());
        OffsetDateTime expiraEn = OffsetDateTime.ofInstant(jwtService.expiracionAccessToken(), ZoneOffset.UTC);

        String refreshTokenValue = jwtService.generarTokenOpacoRefresh();
        String refreshTokenHash = jwtService.hashSha256(refreshTokenValue);
        OffsetDateTime expiraRefresh = OffsetDateTime.ofInstant(jwtService.expiracionRefreshToken(), ZoneOffset.UTC);
        refreshTokenRepository.save(new RefreshToken(usuario, refreshTokenHash, expiraRefresh));

        return new TokenResponseDTO(accessToken, refreshTokenValue, expiraEn);
    }
}
