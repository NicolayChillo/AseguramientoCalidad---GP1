package com.proyecto.backend_reservas_hoteleria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.proyecto.backend_reservas_hoteleria.dto.reserva.ReservaRequest;
import com.proyecto.backend_reservas_hoteleria.dto.reserva.ReservaResponse;
import com.proyecto.backend_reservas_hoteleria.model.Habitacion;
import com.proyecto.backend_reservas_hoteleria.model.Reserva;
import com.proyecto.backend_reservas_hoteleria.model.TipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.Usuario;
import com.proyecto.backend_reservas_hoteleria.model.enums.Estado;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoReserva;
import com.proyecto.backend_reservas_hoteleria.model.enums.NombreTipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.repository.HabitacionRepository;
import com.proyecto.backend_reservas_hoteleria.repository.ReservaRepository;
import com.proyecto.backend_reservas_hoteleria.repository.UsuarioRepository;

/**
 * Pruebas unitarias del modulo de reservas.
 * Valida creacion, calculo de precios, disponibilidad, confirmacion y cancelacion.
 */
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private HabitacionRepository habitacionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ReservaService reservaService;

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 10);
    private static final LocalDate FIN = LocalDate.of(2026, 3, 13);

    private TipoHabitacion tipo(int capacidad, String precio) {
        TipoHabitacion tipo = new TipoHabitacion(
                NombreTipoHabitacion.DOBLE, "Habitacion doble", capacidad,
                new BigDecimal(precio), Estado.ACTIVO);
        tipo.setIdTipoHabitacion(1L);
        return tipo;
    }

    private Habitacion habitacion(Long id, EstadoHabitacion estado, TipoHabitacion tipo) {
        Habitacion habitacion = new Habitacion("HAB-101", "Habitacion de prueba", "http://img", 1, estado, tipo);
        habitacion.setIdHabitacion(id);
        return habitacion;
    }

    private Usuario usuario(Long id, String correo) {
        Usuario usuario = new Usuario("Ana Torres", correo, "hash", LocalDate.now(), Estado.ACTIVO, null);
        usuario.setIdUsuario(id);
        return usuario;
    }

    private Reserva reserva(Long id, EstadoReserva estado, Habitacion habitacion, Usuario usuario) {
        Reserva reserva = new Reserva(LocalDate.now(), INICIO, FIN, 2, estado,
                new BigDecimal("240.00"), "observacion", usuario, habitacion);
        reserva.setIdReserva(id);
        return reserva;
    }

    private ReservaRequest request(Long idUsuario, Long idHabitacion, LocalDate inicio, LocalDate fin, int huespedes) {
        return new ReservaRequest(idUsuario, idHabitacion, inicio, fin, huespedes, "observacion");
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("crear: calcula el total como precioBase * noches y deja la reserva PENDIENTE")
    void crearCalculaTotalYEstadoPendiente() {
        Habitacion habitacion = habitacion(1L, EstadoHabitacion.DISPONIBLE, tipo(3, "100.00"));
        Usuario usuario = usuario(2L, "ana@test.com");

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(habitacion));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                1L, FIN, INICIO)).thenReturn(List.of());
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva guardada = invocation.getArgument(0);
            guardada.setIdReserva(99L);
            return guardada;
        });

        ReservaResponse response = reservaService.crear(request(2L, 1L, INICIO, FIN, 2));

        assertThat(response.getIdReserva()).isEqualTo(99L);
        assertThat(response.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(response.getTotal()).isEqualByComparingTo("300.00");
        assertThat(response.getFechaReserva()).isEqualTo(LocalDate.now());
        assertThat(response.getIdUsuario()).isEqualTo(2L);
        assertThat(response.getIdHabitacion()).isEqualTo(1L);
        assertThat(response.getHabitacion().getCodigo()).isEqualTo("HAB-101");
        assertThat(response.getHabitacion().getTipoHabitacion().getNombreTipo()).isEqualTo("DOBLE");
        assertThat(response.getUsuario().getCorreo()).isEqualTo("ana@test.com");
        verify(reservaRepository).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear: rechaza fecha de inicio igual o posterior a la fecha fin")
    void crearRechazaFechasInvalidas() {
        ReservaRequest request = request(2L, 1L, FIN, INICIO, 2);

        assertThatThrownBy(() -> reservaService.crear(request))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));

        verify(habitacionRepository, never()).findById(any());
        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear: devuelve 404 cuando la habitacion no existe")
    void crearHabitacionNoEncontrada() {
        when(habitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.crear(request(2L, 1L, INICIO, FIN, 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("crear: rechaza habitaciones que no estan en estado DISPONIBLE")
    void crearHabitacionNoDisponible() {
        Habitacion habitacion = habitacion(1L, EstadoHabitacion.MANTENIMIENTO, tipo(3, "100.00"));
        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(habitacion));

        assertThatThrownBy(() -> reservaService.crear(request(2L, 1L, INICIO, FIN, 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("crear: devuelve 404 cuando el usuario no existe")
    void crearUsuarioNoEncontrado() {
        when(habitacionRepository.findById(1L))
                .thenReturn(Optional.of(habitacion(1L, EstadoHabitacion.DISPONIBLE, tipo(3, "100.00"))));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.crear(request(2L, 1L, INICIO, FIN, 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("crear: devuelve 409 cuando ya existe una reserva CONFIRMADA en las fechas")
    void crearDetectaSolapamientoConfirmado() {
        Habitacion habitacion = habitacion(1L, EstadoHabitacion.DISPONIBLE, tipo(3, "100.00"));
        Usuario usuario = usuario(2L, "ana@test.com");
        Reserva confirmada = reserva(7L, EstadoReserva.CONFIRMADA, habitacion, usuario);

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(habitacion));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                1L, FIN, INICIO)).thenReturn(List.of(confirmada));

        assertThatThrownBy(() -> reservaService.crear(request(2L, 1L, INICIO, FIN, 2)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.CONFLICT));

        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear: permite reservar si el solapamiento solo tiene reservas PENDIENTES")
    void crearPermiteSolapamientoConPendientes() {
        Habitacion habitacion = habitacion(1L, EstadoHabitacion.DISPONIBLE, tipo(3, "100.00"));
        Usuario usuario = usuario(2L, "ana@test.com");
        Reserva pendiente = reserva(7L, EstadoReserva.PENDIENTE, habitacion, usuario);

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(habitacion));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                1L, FIN, INICIO)).thenReturn(List.of(pendiente));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservaResponse response = reservaService.crear(request(2L, 1L, INICIO, FIN, 2));

        assertThat(response.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        verify(reservaRepository).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear: rechaza cuando los huespedes superan la capacidad maxima del tipo")
    void crearRechazaCapacidadExcedida() {
        Habitacion habitacion = habitacion(1L, EstadoHabitacion.DISPONIBLE, tipo(2, "100.00"));
        Usuario usuario = usuario(2L, "ana@test.com");

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(habitacion));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                1L, FIN, INICIO)).thenReturn(List.of());

        assertThatThrownBy(() -> reservaService.crear(request(2L, 1L, INICIO, FIN, 3)))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));

        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear: total en cero cuando la habitacion no tiene tipo asignado")
    void crearSinTipoAsignaTotalCero() {
        Habitacion habitacion = habitacion(1L, EstadoHabitacion.DISPONIBLE, null);
        Usuario usuario = usuario(2L, "ana@test.com");

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(habitacion));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                1L, FIN, INICIO)).thenReturn(List.of());
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservaResponse response = reservaService.crear(request(2L, 1L, INICIO, FIN, 2));

        assertThat(response.getTotal()).isEqualByComparingTo("0");
        assertThat(response.getHabitacion().getTipoHabitacion()).isNull();
    }

    @Test
    @DisplayName("confirmar: cambia a CONFIRMADA y cancela las reservas PENDIENTES traslapadas")
    void confirmarCancelaPendientesTraslapadas() {
        Habitacion habitacion = habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00"));
        Usuario usuario = usuario(2L, "ana@test.com");
        Reserva actual = reserva(1L, EstadoReserva.PENDIENTE, habitacion, usuario);
        Reserva otra = reserva(2L, EstadoReserva.PENDIENTE, habitacion, usuario);

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(actual));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                10L, FIN, INICIO)).thenReturn(List.of(actual, otra));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservaService.confirmar(1L);

        assertThat(actual.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(otra.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(reservaRepository, times(2)).save(any(Reserva.class));
    }

    @Test
    @DisplayName("confirmar: rechaza confirmar una reserva CANCELADA")
    void confirmarRechazaReservaCancelada() {
        Habitacion habitacion = habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00"));
        Reserva cancelada = reserva(1L, EstadoReserva.CANCELADA, habitacion, usuario(2L, "ana@test.com"));
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(cancelada));

        assertThatThrownBy(() -> reservaService.confirmar(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));

        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    @DisplayName("confirmar: devuelve 409 si otra reserva ya esta CONFIRMADA en las fechas")
    void confirmarDetectaConflicto() {
        Habitacion habitacion = habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00"));
        Usuario usuario = usuario(2L, "ana@test.com");
        Reserva actual = reserva(1L, EstadoReserva.PENDIENTE, habitacion, usuario);
        Reserva confirmada = reserva(2L, EstadoReserva.CONFIRMADA, habitacion, usuario);

        when(reservaRepository.findById(1L)).thenReturn(Optional.of(actual));
        when(reservaRepository.findByHabitacionIdHabitacionAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                10L, FIN, INICIO)).thenReturn(List.of(actual, confirmada));

        assertThatThrownBy(() -> reservaService.confirmar(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.CONFLICT));

        verify(reservaRepository, never()).save(any(Reserva.class));
    }

    @Test
    @DisplayName("confirmar: devuelve 404 cuando la reserva no existe")
    void confirmarReservaNoEncontrada() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.confirmar(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("cancelar: cambia el estado de la reserva a CANCELADA")
    void cancelarCambiaEstado() {
        Reserva pendiente = reserva(1L, EstadoReserva.PENDIENTE,
                habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00")), usuario(2L, "ana@test.com"));
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(pendiente));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservaService.cancelar(1L);

        assertThat(pendiente.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(reservaRepository).save(pendiente);
    }

    @Test
    @DisplayName("cancelar: devuelve 404 cuando la reserva no existe")
    void cancelarReservaNoEncontrada() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("obtener: mapea correctamente la reserva y sus datos relacionados")
    void obtenerMapeaReserva() {
        Habitacion habitacion = habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00"));
        Reserva reserva = reserva(5L, EstadoReserva.CONFIRMADA, habitacion, usuario(2L, "ana@test.com"));
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

        ReservaResponse response = reservaService.obtener(5L);

        assertThat(response.getIdReserva()).isEqualTo(5L);
        assertThat(response.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(response.getHabitacion().getCodigo()).isEqualTo("HAB-101");
        assertThat(response.getUsuario().getNombre()).isEqualTo("Ana Torres");
    }

    @Test
    @DisplayName("obtener: devuelve 404 cuando la reserva no existe")
    void obtenerReservaNoEncontrada() {
        when(reservaRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.obtener(5L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("listar: devuelve todas las reservas mapeadas")
    void listarDevuelveTodas() {
        Habitacion habitacion = habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00"));
        Usuario usuario = usuario(2L, "ana@test.com");
        when(reservaRepository.findAll()).thenReturn(List.of(
                reserva(1L, EstadoReserva.PENDIENTE, habitacion, usuario),
                reserva(2L, EstadoReserva.CONFIRMADA, habitacion, usuario)));

        List<ReservaResponse> reservas = reservaService.listar();

        assertThat(reservas).hasSize(2);
        assertThat(reservas).extracting(ReservaResponse::getIdReserva).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("listarPorCorreoUsuario: filtra las reservas del usuario autenticado")
    void listarPorCorreoUsuario() {
        Usuario usuario = usuario(2L, "ana@test.com");
        when(usuarioRepository.findByCorreo("ana@test.com")).thenReturn(Optional.of(usuario));
        when(reservaRepository.findByUsuarioIdUsuario(2L)).thenReturn(List.of(
                reserva(1L, EstadoReserva.PENDIENTE,
                        habitacion(10L, EstadoHabitacion.DISPONIBLE, tipo(3, "80.00")), usuario)));

        List<ReservaResponse> reservas = reservaService.listarPorCorreoUsuario("ana@test.com");

        assertThat(reservas).hasSize(1);
        assertThat(reservas.get(0).getIdUsuario()).isEqualTo(2L);
    }

    @Test
    @DisplayName("listarPorCorreoUsuario: devuelve 404 si el correo no pertenece a un usuario")
    void listarPorCorreoUsuarioNoEncontrado() {
        when(usuarioRepository.findByCorreo("nadie@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.listarPorCorreoUsuario("nadie@test.com"))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }
}
