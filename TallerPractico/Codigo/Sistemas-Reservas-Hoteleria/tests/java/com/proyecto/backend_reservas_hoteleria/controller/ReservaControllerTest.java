package com.proyecto.backend_reservas_hoteleria.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.proyecto.backend_reservas_hoteleria.dto.reserva.ReservaRequest;
import com.proyecto.backend_reservas_hoteleria.dto.reserva.ReservaResponse;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoReserva;
import com.proyecto.backend_reservas_hoteleria.service.ReservaService;

/**
 * Pruebas de integracion de la capa web del modulo de reservas (MockMvc).
 * Verifica rutas, codigos HTTP, validacion de entrada y serializacion JSON.
 */
class ReservaControllerTest {

    private ReservaService reservaService;
    private MockMvc mockMvc;

    private static final String RESERVA_JSON = """
            {
              "idUsuario": 2,
              "idHabitacion": 1,
              "fechaInicio": "2026-03-10",
              "fechaFin": "2026-03-13",
              "numeroHuespedes": 2,
              "observacion": "prueba"
            }
            """;

    @BeforeEach
    void setUp() {
        reservaService = mock(ReservaService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservaController(reservaService))
                .setValidator(validator)
                .build();
    }

    private ReservaResponse respuesta(Long id, EstadoReserva estado) {
        ReservaResponse response = new ReservaResponse(id, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 13), 2, estado,
                new BigDecimal("300.00"), "observacion", 2L, 1L);
        response.setHabitacion(new ReservaResponse.HabitacionInfo("HAB-101",
                new ReservaResponse.TipoHabitacionInfo("DOBLE")));
        response.setUsuario(new ReservaResponse.UsuarioInfo("Ana Torres", "ana@test.com"));
        return response;
    }

    @Test
    @DisplayName("POST /reservas: crea la reserva y responde 200 con el JSON esperado")
    void crearReserva() throws Exception {
        when(reservaService.crear(any(ReservaRequest.class)))
                .thenReturn(respuesta(9L, EstadoReserva.PENDIENTE));

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESERVA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva").value(9))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.habitacion.codigo").value("HAB-101"))
                .andExpect(jsonPath("$.usuario.correo").value("ana@test.com"));

        verify(reservaService).crear(any(ReservaRequest.class));
    }

    @Test
    @DisplayName("POST /reservas: responde 400 cuando falta un campo obligatorio")
    void crearReservaSinFechas() throws Exception {
        String jsonSinFecha = """
                {
                  "idUsuario": 2,
                  "idHabitacion": 1,
                  "fechaFin": "2026-03-13",
                  "numeroHuespedes": 2
                }
                """;

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonSinFecha))
                .andExpect(status().isBadRequest());

        verify(reservaService, never()).crear(any(ReservaRequest.class));
    }

    @Test
    @DisplayName("GET /reservas: lista todas las reservas")
    void listarReservas() throws Exception {
        when(reservaService.listar()).thenReturn(List.of(
                respuesta(1L, EstadoReserva.PENDIENTE),
                respuesta(2L, EstadoReserva.CONFIRMADA)));

        mockMvc.perform(get("/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idReserva").value(1))
                .andExpect(jsonPath("$[1].estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("GET /reservas/{id}: obtiene una reserva por id")
    void obtenerReserva() throws Exception {
        when(reservaService.obtener(5L)).thenReturn(respuesta(5L, EstadoReserva.CONFIRMADA));

        mockMvc.perform(get("/reservas/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva").value(5))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        verify(reservaService).obtener(5L);
    }

    @Test
    @DisplayName("PUT /reservas/{id}/cancelar: responde 204 y delega al servicio")
    void cancelarReserva() throws Exception {
        mockMvc.perform(put("/reservas/7/cancelar"))
                .andExpect(status().isNoContent());

        verify(reservaService).cancelar(7L);
    }

    @Test
    @DisplayName("PUT /reservas/{id}/confirmar: responde 204 y delega al servicio")
    void confirmarReserva() throws Exception {
        mockMvc.perform(put("/reservas/8/confirmar"))
                .andExpect(status().isNoContent());

        verify(reservaService).confirmar(8L);
    }

    @Test
    @DisplayName("GET /reservas/mias: usa el correo del usuario autenticado")
    void listarReservasDelUsuario() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("ana@test.com");
        when(reservaService.listarPorCorreoUsuario("ana@test.com"))
                .thenReturn(List.of(respuesta(1L, EstadoReserva.PENDIENTE)));

        mockMvc.perform(get("/reservas/mias").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usuario.correo").value("ana@test.com"));

        verify(reservaService).listarPorCorreoUsuario("ana@test.com");
    }
}
