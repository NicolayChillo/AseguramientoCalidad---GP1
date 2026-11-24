package com.proyecto.backend_reservas_hoteleria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.proyecto.backend_reservas_hoteleria.model.Rol;
import com.proyecto.backend_reservas_hoteleria.model.Usuario;
import com.proyecto.backend_reservas_hoteleria.model.enums.Estado;
import com.proyecto.backend_reservas_hoteleria.model.enums.TipoRol;
import com.proyecto.backend_reservas_hoteleria.repository.UsuarioRepository;

/**
 * Pruebas unitarias de la carga de usuarios para Spring Security.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private Usuario usuario(Estado estado, TipoRol tipoRol) {
        Rol rol = tipoRol != null ? new Rol("Rol", tipoRol, Estado.ACTIVO) : null;
        return new Usuario("Ana Torres", "ana@test.com", "hash-bcrypt", LocalDate.now(), estado, rol);
    }

    @Test
    @DisplayName("loadUserByUsername: carga un usuario ACTIVO con su rol como autoridad")
    void cargaUsuarioActivo() {
        when(usuarioRepository.findByCorreo("ana@test.com"))
                .thenReturn(Optional.of(usuario(Estado.ACTIVO, TipoRol.CLIENTE)));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("ana@test.com");

        assertThat(userDetails.getUsername()).isEqualTo("ana@test.com");
        assertThat(userDetails.getPassword()).isEqualTo("hash-bcrypt");
        assertThat(userDetails.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_CLIENTE");
    }

    @Test
    @DisplayName("loadUserByUsername: rechaza usuarios INACTIVOS")
    void rechazaUsuarioInactivo() {
        when(usuarioRepository.findByCorreo("ana@test.com"))
                .thenReturn(Optional.of(usuario(Estado.INACTIVO, TipoRol.CLIENTE)));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("ana@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuario inactivo");
    }

    @Test
    @DisplayName("loadUserByUsername: lanza UsernameNotFoundException si el correo no existe")
    void usuarioNoEncontrado() {
        when(usuarioRepository.findByCorreo("nadie@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("nadie@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("loadUserByUsername: no asigna autoridades cuando el usuario no tiene rol")
    void usuarioSinRol() {
        when(usuarioRepository.findByCorreo("ana@test.com"))
                .thenReturn(Optional.of(usuario(Estado.ACTIVO, null)));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("ana@test.com");

        assertThat(userDetails.getAuthorities()).isEmpty();
    }
}
