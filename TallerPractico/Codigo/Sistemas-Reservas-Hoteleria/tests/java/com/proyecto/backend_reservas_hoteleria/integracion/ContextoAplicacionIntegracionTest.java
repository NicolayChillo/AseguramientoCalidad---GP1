package com.proyecto.backend_reservas_hoteleria.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.proyecto.backend_reservas_hoteleria.model.enums.TipoRol;
import com.proyecto.backend_reservas_hoteleria.repository.RolRepository;
import com.proyecto.backend_reservas_hoteleria.service.AuthService;
import com.proyecto.backend_reservas_hoteleria.service.ReservaService;

/**
 * Prueba de integracion de arranque: verifica que el contexto Spring Boot
 * completo levanta con H2 y que los roles base quedan sembrados.
 */
@SpringBootTest
class ContextoAplicacionIntegracionTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private RolRepository rolRepository;

    @Test
    @DisplayName("Integracion: el contexto de la aplicacion carga y expone los servicios principales")
    void cargaContextoAplicacion() {
        assertThat(contexto).isNotNull();
        assertThat(contexto.getBean(ReservaService.class)).isNotNull();
        assertThat(contexto.getBean(AuthService.class)).isNotNull();
    }

    @Test
    @DisplayName("Integracion: SeedDataConfig siembra los roles ADMINISTRADOR y CLIENTE")
    void siembraRolesBase() {
        assertThat(rolRepository.findByTipoRol(TipoRol.ADMINISTRADOR)).isPresent();
        assertThat(rolRepository.findByTipoRol(TipoRol.CLIENTE)).isPresent();
    }
}
