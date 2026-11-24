package com.proyecto.backend_reservas_hoteleria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;

import com.proyecto.backend_reservas_hoteleria.config.JwtProperties;

import io.jsonwebtoken.JwtException;

/**
 * Pruebas unitarias de la generacion y validacion de tokens JWT.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("OaVIClutqg9Udd/QWvjWMFBttX3dFZz47Bd9XjNKzOBUdBeO7Su8zYkxFqDsTJsxwdSx2E+9ta/uJblR6uiW9w==");
        properties.setExpirationMs(3_600_000L);
        jwtService = new JwtService(properties);
    }

    private UserDetails userDetails(String correo) {
        return User.withUsername(correo).password("hash").roles("CLIENTE").build();
    }

    @Test
    @DisplayName("generarToken: crea un JWT valido y extrae el usuario")
    void generarTokenValido() {
        UserDetails user = userDetails("ana@test.com");

        String token = jwtService.generarToken(user);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.extraerUsuario(token)).isEqualTo("ana@test.com");
        assertThat(jwtService.esTokenValido(token, user)).isTrue();
    }

    @Test
    @DisplayName("esTokenValido: rechaza el token cuando el usuario no coincide")
    void tokenNoCoincideConUsuario() {
        String token = jwtService.generarToken(userDetails("ana@test.com"));

        assertThat(jwtService.esTokenValido(token, userDetails("otro@test.com"))).isFalse();
    }

    @Test
    @DisplayName("extraerUsuario: lanza JwtException con un token mal formado")
    void tokenMalFormadoLanzaExcepcion() {
        assertThatThrownBy(() -> jwtService.extraerUsuario("esto.no.es.un.jwt"))
                .isInstanceOf(JwtException.class);
    }
}
