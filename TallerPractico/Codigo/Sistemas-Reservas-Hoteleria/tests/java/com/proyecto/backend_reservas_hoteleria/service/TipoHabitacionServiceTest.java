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

import com.proyecto.backend_reservas_hoteleria.dto.tipohabitacion.TipoHabitacionRequest;
import com.proyecto.backend_reservas_hoteleria.dto.tipohabitacion.TipoHabitacionResponse;
import com.proyecto.backend_reservas_hoteleria.model.TipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.enums.Estado;
import com.proyecto.backend_reservas_hoteleria.model.enums.NombreTipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.repository.TipoHabitacionRepository;

/**
 * Pruebas unitarias del CRUD de tipos de habitacion (precio base y capacidad).
 */
@ExtendWith(MockitoExtension.class)
class TipoHabitacionServiceTest {

    @Mock
    private TipoHabitacionRepository tipoHabitacionRepository;

    @InjectMocks
    private TipoHabitacionService tipoHabitacionService;

    private TipoHabitacion tipo(Long id, NombreTipoHabitacion nombre, int capacidad, String precio) {
        TipoHabitacion tipo = new TipoHabitacion(nombre, "Descripcion", capacidad,
                new BigDecimal(precio), Estado.ACTIVO);
        tipo.setIdTipoHabitacion(id);
        return tipo;
    }

    private TipoHabitacionRequest request(NombreTipoHabitacion nombre, int capacidad, String precio) {
        return new TipoHabitacionRequest(nombre, "Descripcion", capacidad, new BigDecimal(precio), Estado.ACTIVO);
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(status);
    }

    @Test
    @DisplayName("crear: guarda el tipo de habitacion con su precio y capacidad")
    void crearGuardaTipo() {
        when(tipoHabitacionRepository.save(any(TipoHabitacion.class))).thenAnswer(invocation -> {
            TipoHabitacion guardado = invocation.getArgument(0);
            guardado.setIdTipoHabitacion(3L);
            return guardado;
        });

        TipoHabitacionResponse response = tipoHabitacionService.crear(
                request(NombreTipoHabitacion.SUITE, 4, "200.00"));

        assertThat(response.getIdTipoHabitacion()).isEqualTo(3L);
        assertThat(response.getNombre()).isEqualTo(NombreTipoHabitacion.SUITE);
        assertThat(response.getCapacidadMaxima()).isEqualTo(4);
        assertThat(response.getPrecioBase()).isEqualByComparingTo("200.00");
        assertThat(response.getEstado()).isEqualTo(Estado.ACTIVO);
    }

    @Test
    @DisplayName("listar: devuelve todos los tipos de habitacion")
    void listarDevuelveTodos() {
        when(tipoHabitacionRepository.findAll()).thenReturn(List.of(
                tipo(1L, NombreTipoHabitacion.SIMPLE, 1, "50.00"),
                tipo(2L, NombreTipoHabitacion.DOBLE, 2, "80.00")));

        List<TipoHabitacionResponse> tipos = tipoHabitacionService.listar();

        assertThat(tipos).hasSize(2);
        assertThat(tipos).extracting(TipoHabitacionResponse::getNombre)
                .containsExactly(NombreTipoHabitacion.SIMPLE, NombreTipoHabitacion.DOBLE);
    }

    @Test
    @DisplayName("obtener: retorna el tipo solicitado")
    void obtenerExistente() {
        when(tipoHabitacionRepository.findById(1L))
                .thenReturn(Optional.of(tipo(1L, NombreTipoHabitacion.ESTUDIO, 2, "90.00")));

        TipoHabitacionResponse response = tipoHabitacionService.obtener(1L);

        assertThat(response.getIdTipoHabitacion()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo(NombreTipoHabitacion.ESTUDIO);
    }

    @Test
    @DisplayName("obtener: devuelve 404 si el tipo no existe")
    void obtenerNoExiste() {
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tipoHabitacionService.obtener(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("actualizar: modifica los campos del tipo existente")
    void actualizarModificaCampos() {
        TipoHabitacion existente = tipo(1L, NombreTipoHabitacion.SIMPLE, 1, "50.00");
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(tipoHabitacionRepository.save(any(TipoHabitacion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TipoHabitacionResponse response = tipoHabitacionService.actualizar(1L,
                request(NombreTipoHabitacion.DOBLE, 3, "110.00"));

        assertThat(response.getNombre()).isEqualTo(NombreTipoHabitacion.DOBLE);
        assertThat(response.getCapacidadMaxima()).isEqualTo(3);
        assertThat(response.getPrecioBase()).isEqualByComparingTo("110.00");
    }

    @Test
    @DisplayName("actualizar: devuelve 404 si el tipo no existe")
    void actualizarNoExiste() {
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tipoHabitacionService.actualizar(1L,
                request(NombreTipoHabitacion.DOBLE, 3, "110.00")))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));

        verify(tipoHabitacionRepository, never()).save(any(TipoHabitacion.class));
    }

    @Test
    @DisplayName("eliminar: borra el tipo existente")
    void eliminarExistente() {
        TipoHabitacion existente = tipo(1L, NombreTipoHabitacion.SIMPLE, 1, "50.00");
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.of(existente));

        tipoHabitacionService.eliminar(1L);

        verify(tipoHabitacionRepository).delete(existente);
    }

    @Test
    @DisplayName("eliminar: devuelve 404 si el tipo no existe")
    void eliminarNoExiste() {
        when(tipoHabitacionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tipoHabitacionService.eliminar(1L))
                .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));

        verify(tipoHabitacionRepository, never()).delete(any(TipoHabitacion.class));
    }
}
