package com.proyecto.backend_reservas_hoteleria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.proyecto.backend_reservas_hoteleria.dto.habitacion.HabitacionRequest;
import com.proyecto.backend_reservas_hoteleria.dto.habitacion.HabitacionResponse;
import com.proyecto.backend_reservas_hoteleria.model.Habitacion;
import com.proyecto.backend_reservas_hoteleria.model.TipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.enums.Estado;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.enums.NombreTipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.repository.HabitacionRepository;
import com.proyecto.backend_reservas_hoteleria.repository.TipoHabitacionRepository;

/**
 * Pruebas unitarias del modulo de gestion de habitaciones (CRUD y filtros).
 */
@ExtendWith(MockitoExtension.class)
class HabitacionServiceTest {

    @Mock
    private HabitacionRepository habitacionRepository;

    @Mock
    private TipoHabitacionRepository tipoHabitacionRepository;

    @InjectMocks
    private HabitacionService habitacionService;

    private TipoHabitacion tipo(Long id, NombreTipoHabitacion nombre, int capacidad, String precio) {
        TipoHabitacion tipo = new TipoHabitacion(nombre, "Tipo de prueba", capacidad,
                new BigDecimal(precio), Estado.ACTIVO);
        tipo.setIdTipoHabitacion(id);
        return tipo;
    }

    private Habitacion habitacion(Long id, String codigo, EstadoHabitacion estado, TipoHabitacion tipo) {
        Habitacion habitacion = new Habitacion(codigo, "Descripcion", "http://imagen", 2, estado, tipo);
        habitacion.setIdHabitacion(id);
        return habitacion;
    }

    private HabitacionRequest request() {
        return new HabitacionRequest("HAB-202", "Nueva habitacion", "http://imagen", 2,
                EstadoHabitacion.DISPONIBLE, 1L);
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("crear: guarda la habitacion vinculada al tipo indicado")
    void crearGuardaHabitacion() {
        TipoHabitacion tipo = tipo(1L, NombreTipoHabitacion.SUITE, 4, "150.00");
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(habitacionRepository.save(any(Habitacion.class))).thenAnswer(invocation -> {
            Habitacion guardada = invocation.getArgument(0);
            guardada.setIdHabitacion(10L);
            return guardada;
        });

        HabitacionResponse response = habitacionService.crear(request());

        assertThat(response.getIdHabitacion()).isEqualTo(10L);
        assertThat(response.getCodigo()).isEqualTo("HAB-202");
        assertThat(response.getEstado()).isEqualTo(EstadoHabitacion.DISPONIBLE);
        assertThat(response.getIdTipoHabitacion()).isEqualTo(1L);
        assertThat(response.getTipoHabitacionNombre()).isEqualTo("SUITE");
        assertThat(response.getPrecioBase()).isEqualByComparingTo("150.00");
        assertThat(response.getCapacidadMaxima()).isEqualTo(4);
    }

    @Test
    @DisplayName("crear: devuelve 400 si el tipo de habitacion no existe")
    void crearTipoNoExiste() {
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitacionService.crear(request()))
                .satisfies(ex -> assertStatus(ex, HttpStatus.BAD_REQUEST));

        verify(habitacionRepository, never()).save(any(Habitacion.class));
    }

    @Test
    @DisplayName("listar: devuelve todas las habitaciones mapeadas")
    void listarDevuelveTodas() {
        TipoHabitacion tipo = tipo(1L, NombreTipoHabitacion.SIMPLE, 1, "50.00");
        when(habitacionRepository.findAll()).thenReturn(List.of(
                habitacion(1L, "HAB-101", EstadoHabitacion.DISPONIBLE, tipo),
                habitacion(2L, "HAB-102", EstadoHabitacion.OCUPADA, tipo)));

        List<HabitacionResponse> habitaciones = habitacionService.listar();

        assertThat(habitaciones).hasSize(2);
        assertThat(habitaciones).extracting(HabitacionResponse::getCodigo)
                .containsExactly("HAB-101", "HAB-102");
    }

    @Test
    @DisplayName("obtener: retorna la habitacion existente")
    void obtenerExistente() {
        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(
                habitacion(1L, "HAB-101", EstadoHabitacion.DISPONIBLE,
                        tipo(1L, NombreTipoHabitacion.DOBLE, 2, "80.00"))));

        HabitacionResponse response = habitacionService.obtener(1L);

        assertThat(response.getIdHabitacion()).isEqualTo(1L);
        assertThat(response.getTipoHabitacionNombre()).isEqualTo("DOBLE");
    }

    @Test
    @DisplayName("obtener: devuelve 404 si la habitacion no existe")
    void obtenerNoExiste() {
        when(habitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitacionService.obtener(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("actualizar: conserva la imagen actual cuando no se envia una nueva")
    void actualizarConservaImagen() {
        Habitacion existente = habitacion(1L, "HAB-101", EstadoHabitacion.DISPONIBLE,
                tipo(1L, NombreTipoHabitacion.DOBLE, 2, "80.00"));
        existente.setImagen("http://imagen-vieja");

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.of(existente.getTipoHabitacion()));
        when(habitacionRepository.save(any(Habitacion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitacionResponse response = habitacionService.actualizar(1L, "HAB-999", "Actualizada", 3,
                EstadoHabitacion.MANTENIMIENTO, 1L, null);

        assertThat(response.getCodigo()).isEqualTo("HAB-999");
        assertThat(response.getEstado()).isEqualTo(EstadoHabitacion.MANTENIMIENTO);
        assertThat(response.getImagen()).isEqualTo("http://imagen-vieja");
    }

    @Test
    @DisplayName("actualizar: reemplaza la imagen cuando se envia una URL nueva")
    void actualizarReemplazaImagen() {
        Habitacion existente = habitacion(1L, "HAB-101", EstadoHabitacion.DISPONIBLE,
                tipo(1L, NombreTipoHabitacion.DOBLE, 2, "80.00"));
        existente.setImagen("http://imagen-vieja");

        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.of(existente.getTipoHabitacion()));
        when(habitacionRepository.save(any(Habitacion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitacionResponse response = habitacionService.actualizar(1L, "HAB-101", "Actualizada", 2,
                EstadoHabitacion.DISPONIBLE, 1L, "http://imagen-nueva");

        assertThat(response.getImagen()).isEqualTo("http://imagen-nueva");
    }

    @Test
    @DisplayName("actualizar: devuelve 404 si la habitacion no existe")
    void actualizarNoExiste() {
        when(habitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitacionService.actualizar(1L, "HAB-101", "x", 2,
                EstadoHabitacion.DISPONIBLE, 1L, null))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("eliminar: borra la habitacion existente")
    void eliminarExistente() {
        Habitacion existente = habitacion(1L, "HAB-101", EstadoHabitacion.DISPONIBLE, null);
        when(habitacionRepository.findById(1L)).thenReturn(Optional.of(existente));

        habitacionService.eliminar(1L);

        verify(habitacionRepository).delete(existente);
    }

    @Test
    @DisplayName("eliminar: devuelve 404 si la habitacion no existe")
    void eliminarNoExiste() {
        when(habitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitacionService.eliminar(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));

        verify(habitacionRepository, never()).delete(any(Habitacion.class));
    }

    @Test
    @DisplayName("listarPorTipo: filtra las habitaciones por nombre de tipo")
    void listarPorTipo() {
        TipoHabitacion simple = tipo(1L, NombreTipoHabitacion.SIMPLE, 1, "50.00");
        when(habitacionRepository.findByTipoHabitacionNombre(NombreTipoHabitacion.SIMPLE))
                .thenReturn(List.of(habitacion(1L, "HAB-101", EstadoHabitacion.DISPONIBLE, simple)));

        List<HabitacionResponse> habitaciones = habitacionService.listarPorTipo(NombreTipoHabitacion.SIMPLE);

        assertThat(habitaciones).hasSize(1);
        assertThat(habitaciones.get(0).getTipoHabitacionNombre()).isEqualTo("SIMPLE");
    }
}
