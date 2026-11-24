package com.proyecto.backend_reservas_hoteleria.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.proyecto.backend_reservas_hoteleria.dto.reserva.ReservaRequest;
import com.proyecto.backend_reservas_hoteleria.dto.reserva.ReservaResponse;
import com.proyecto.backend_reservas_hoteleria.model.Habitacion;
import com.proyecto.backend_reservas_hoteleria.model.TipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.Usuario;
import com.proyecto.backend_reservas_hoteleria.model.enums.Estado;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoReserva;
import com.proyecto.backend_reservas_hoteleria.model.enums.NombreTipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.repository.HabitacionRepository;
import com.proyecto.backend_reservas_hoteleria.repository.ReservaRepository;
import com.proyecto.backend_reservas_hoteleria.repository.RolRepository;
import com.proyecto.backend_reservas_hoteleria.repository.TipoHabitacionRepository;
import com.proyecto.backend_reservas_hoteleria.repository.UsuarioRepository;
import com.proyecto.backend_reservas_hoteleria.service.ReservaService;

/**
 * Pruebas de integracion reales del flujo de reservas:
 * controlador de servicio + repositorios JPA + base de datos H2 en memoria.
 */
@SpringBootTest
@Transactional
class ReservaFlujoIntegracionTest {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private HabitacionRepository habitacionRepository;

    @Autowired
    private TipoHabitacionRepository tipoHabitacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    private Habitacion habitacion;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        TipoHabitacion tipo = tipoHabitacionRepository.save(new TipoHabitacion(
                NombreTipoHabitacion.DOBLE, "Habitacion doble de integracion", 3,
                new BigDecimal("80.00"), Estado.ACTIVO));

        habitacion = habitacionRepository.save(new Habitacion(
                "HAB-IT-01", "Habitacion de integracion", "http://imagen", 1,
                EstadoHabitacion.DISPONIBLE, tipo));

        usuario = usuarioRepository.save(new Usuario(
                "Cliente Integracion", "integracion@test.com", "hash",
                LocalDate.now(), Estado.ACTIVO,
                rolRepository.findByTipoRol(com.proyecto.backend_reservas_hoteleria.model.enums.TipoRol.CLIENTE)
                        .orElseThrow()));
    }

    private ReservaRequest request(LocalDate inicio, LocalDate fin, int huespedes) {
        return new ReservaRequest(usuario.getIdUsuario(), habitacion.getIdHabitacion(),
                inicio, fin, huespedes, "Reserva de integracion");
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("Integracion: crear -> confirmar -> cancelar persiste cada cambio de estado")
    void flujoCrearConfirmarCancelar() {
        ReservaResponse creada = reservaService.crear(request(
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 13), 2));

        assertThat(creada.getIdReserva()).isNotNull();
        assertThat(creada.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(creada.getTotal()).isEqualByComparingTo("240.00");
        assertThat(reservaRepository.findById(creada.getIdReserva()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE);

        reservaService.confirmar(creada.getIdReserva());
        assertThat(reservaRepository.findById(creada.getIdReserva()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);

        reservaService.cancelar(creada.getIdReserva());
        assertThat(reservaRepository.findById(creada.getIdReserva()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    @DisplayName("Integracion: solo una reserva CONFIRMADA bloquea las fechas y cancela las pendientes")
    void disponibilidadEntreReservasSolapadas() {
        ReservaResponse primera = reservaService.crear(request(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4), 2));
        ReservaResponse segunda = reservaService.crear(request(
                LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 5), 2));

        assertThat(segunda.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);

        reservaService.confirmar(primera.getIdReserva());

        assertThat(reservaRepository.findById(primera.getIdReserva()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reservaRepository.findById(segunda.getIdReserva()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);

        assertThatThrownBy(() -> reservaService.crear(request(
                LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 6), 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Integracion: el total se calcula con las noches reales entre fechas")
    void calculaPrecioSegunNoches() {
        ReservaResponse response = reservaService.crear(request(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 6), 2));

        assertThat(response.getTotal()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Integracion: fechas invalidas no generan registros en la base de datos")
    void fechasInvalidasNoPersisten() {
        long antes = reservaRepository.count();

        assertThatThrownBy(() -> reservaService.crear(request(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));

        assertThat(reservaRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Integracion: rechaza reservas que superan la capacidad del tipo")
    void rechazaCapacidadExcedida() {
        assertThatThrownBy(() -> reservaService.crear(request(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 4)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Integracion: rechaza reservas sobre habitaciones no disponibles")
    void rechazaHabitacionNoDisponible() {
        habitacion.setEstado(EstadoHabitacion.MANTENIMIENTO);
        habitacionRepository.save(habitacion);

        assertThatThrownBy(() -> reservaService.crear(request(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));
    }
}
