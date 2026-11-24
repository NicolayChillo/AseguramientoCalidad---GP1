package com.proyecto.backend_reservas_hoteleria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.proyecto.backend_reservas_hoteleria.dto.auth.LoginRequest;
import com.proyecto.backend_reservas_hoteleria.dto.auth.LoginResponse;
import com.proyecto.backend_reservas_hoteleria.dto.auth.RegisterRequest;
import com.proyecto.backend_reservas_hoteleria.dto.auth.RegisterResponse;
import com.proyecto.backend_reservas_hoteleria.model.Rol;
import com.proyecto.backend_reservas_hoteleria.model.Usuario;
import com.proyecto.backend_reservas_hoteleria.model.enums.Estado;
import com.proyecto.backend_reservas_hoteleria.model.enums.TipoRol;
import com.proyecto.backend_reservas_hoteleria.repository.RolRepository;
import com.proyecto.backend_reservas_hoteleria.repository.UsuarioRepository;

/**
 * Pruebas unitarias del modulo de autenticacion (registro y login con JWT).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private org.springframework.security.authentication.AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private Rol rol(TipoRol tipoRol) {
        return new Rol(tipoRol == TipoRol.CLIENTE ? "Cliente" : "Administrador", tipoRol, Estado.ACTIVO);
    }

    private Usuario usuario(Long id, String correo, TipoRol tipoRol) {
        Usuario usuario = new Usuario("Ana Torres", correo, "hash", LocalDate.now(), Estado.ACTIVO, rol(tipoRol));
        usuario.setIdUsuario(id);
        return usuario;
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("registrar: crea un CLIENTE con contrasena encriptada y estado ACTIVO")
    void registrarCreaCliente() {
        when(usuarioRepository.existsByCorreo("nuevo@test.com")).thenReturn(false);
        when(rolRepository.findByTipoRol(TipoRol.CLIENTE)).thenReturn(Optional.of(rol(TipoRol.CLIENTE)));
        when(passwordEncoder.encode("secreta123")).thenReturn("hash-bcrypt");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario guardado = invocation.getArgument(0);
            guardado.setIdUsuario(15L);
            return guardado;
        });

        RegisterResponse response = authService.registrar(new RegisterRequest("Ana", "nuevo@test.com", "secreta123"));

        assertThat(response.getIdUsuario()).isEqualTo(15L);
        assertThat(response.getCorreo()).isEqualTo("nuevo@test.com");
        assertThat(response.getRol()).isEqualTo("CLIENTE");
        verify(passwordEncoder).encode("secreta123");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("registrar: rechaza correos ya registrados")
    void registrarRechazaCorreoDuplicado() {
        when(usuarioRepository.existsByCorreo("existente@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(
                new RegisterRequest("Ana", "existente@test.com", "secreta123")))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("registrar: falla si el rol CLIENTE no existe en la base de datos")
    void registrarFallaSinRolCliente() {
        when(usuarioRepository.existsByCorreo("nuevo@test.com")).thenReturn(false);
        when(rolRepository.findByTipoRol(TipoRol.CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registrar(
                new RegisterRequest("Ana", "nuevo@test.com", "secreta123")))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("iniciarSesion: autentica y devuelve token, id y rol del usuario")
    void iniciarSesionDevuelveToken() {
        UserDetails userDetails = User.withUsername("admin@test.com").password("hash").roles("ADMINISTRADOR").build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generarToken(userDetails)).thenReturn("jwt-token-123");
        when(usuarioRepository.findByCorreo("admin@test.com"))
                .thenReturn(Optional.of(usuario(5L, "admin@test.com", TipoRol.ADMINISTRADOR)));

        LoginResponse response = authService.iniciarSesion(new LoginRequest("admin@test.com", "secreta123"));

        assertThat(response.getToken()).isEqualTo("jwt-token-123");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getIdUsuario()).isEqualTo(5L);
        assertThat(response.getRol()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    @DisplayName("iniciarSesion: propaga BadCredentialsException con credenciales invalidas")
    void iniciarSesionCredencialesInvalidas() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Credenciales invalidas"));

        assertThatThrownBy(() -> authService.iniciarSesion(new LoginRequest("admin@test.com", "mala")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generarToken(any());
    }

    @Test
    @DisplayName("iniciarSesion: devuelve 404 si el usuario autenticado no se encuentra por correo")
    void iniciarSesionUsuarioNoEncontrado() {
        UserDetails userDetails = User.withUsername("fantasma@test.com").password("hash").roles("CLIENTE").build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generarToken(userDetails)).thenReturn("jwt-token-123");
        when(usuarioRepository.findByCorreo("fantasma@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.iniciarSesion(new LoginRequest("fantasma@test.com", "secreta123")))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }
}
