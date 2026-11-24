package com.proyecto.backend_reservas_hoteleria.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.proyecto.backend_reservas_hoteleria.dto.auth.LoginRequest;
import com.proyecto.backend_reservas_hoteleria.dto.auth.LoginResponse;
import com.proyecto.backend_reservas_hoteleria.dto.auth.RegisterRequest;
import com.proyecto.backend_reservas_hoteleria.dto.auth.RegisterResponse;
import com.proyecto.backend_reservas_hoteleria.service.AuthService;

/**
 * Pruebas de integracion de la capa web del modulo de autenticacion (MockMvc).
 */
class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /auth/registro: registra un cliente y responde 200")
    void registrarUsuario() throws Exception {
        when(authService.registrar(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse(20L, "nuevo@test.com", "CLIENTE"));

        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","correo":"nuevo@test.com","contrasena":"secreta123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario").value(20))
                .andExpect(jsonPath("$.correo").value("nuevo@test.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));

        verify(authService).registrar(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/registro: responde 400 con un correo invalido")
    void registrarCorreoInvalido() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","correo":"no-es-correo","contrasena":"secreta123"}
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).registrar(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /auth/login: devuelve el token JWT y el rol")
    void iniciarSesion() throws Exception {
        when(authService.iniciarSesion(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("jwt-token-123", 5L, "ADMINISTRADOR"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"admin@test.com","contrasena":"secreta123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.idUsuario").value(5))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    @Test
    @DisplayName("POST /auth/login: responde 400 cuando la contrasena esta vacia")
    void iniciarSesionSinContrasena() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo":"admin@test.com","contrasena":""}
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).iniciarSesion(any(LoginRequest.class));
    }
}
