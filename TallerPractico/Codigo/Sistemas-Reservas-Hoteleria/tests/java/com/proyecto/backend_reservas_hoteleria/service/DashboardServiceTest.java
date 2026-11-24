package com.proyecto.backend_reservas_hoteleria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proyecto.backend_reservas_hoteleria.dto.dashboard.DashboardResumenDTO;
import com.proyecto.backend_reservas_hoteleria.dto.dashboard.EstadisticasGeneralesDTO;
import com.proyecto.backend_reservas_hoteleria.dto.dashboard.OcupacionMensualDTO;
import com.proyecto.backend_reservas_hoteleria.dto.dashboard.TopHabitacionDTO;
import com.proyecto.backend_reservas_hoteleria.model.Habitacion;
import com.proyecto.backend_reservas_hoteleria.model.Reserva;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoHabitacion;
import com.proyecto.backend_reservas_hoteleria.model.enums.EstadoReserva;
import com.proyecto.backend_reservas_hoteleria.model.enums.NombreTipoHabitacion;
import com.proyecto.backend_reservas_hoteleria.repository.HabitacionRepository;
import com.proyecto.backend_reservas_hoteleria.repository.ReservaRepository;

/**
 * Pruebas unitarias del modulo Dashboard: KPIs, ocupacion mensual y top de habitaciones.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private HabitacionRepository habitacionRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Habitacion habitacion(Long id, EstadoHabitacion estado) {
        Habitacion habitacion = new Habitacion("HAB-" + id, "Descripcion", "url", 1, estado, null);
        habitacion.setIdHabitacion(id);
        return habitacion;
    }

    private Reserva reserva(EstadoReserva estado) {
        Reserva reserva = new Reserva();
        reserva.setEstado(estado);
        return reserva;
    }

    @Test
    @DisplayName("estadisticas: calcula los KPIs generales del dashboard")
    void calculaEstadisticasGenerales() {
        when(reservaRepository.obtenerTotalIngresos()).thenReturn(new BigDecimal("5000.00"));
        when(reservaRepository.obtenerIngresosPorMesYAño(anyInt(), anyInt())).thenReturn(new BigDecimal("500.00"));
        when(reservaRepository.count()).thenReturn(10L);
        when(reservaRepository.findByEstado(EstadoReserva.CONFIRMADA))
                .thenReturn(List.of(reserva(EstadoReserva.CONFIRMADA), reserva(EstadoReserva.CONFIRMADA)));
        when(habitacionRepository.count()).thenReturn(5L);
        when(habitacionRepository.findAll()).thenReturn(List.of(
                habitacion(1L, EstadoHabitacion.DISPONIBLE),
                habitacion(2L, EstadoHabitacion.DISPONIBLE),
                habitacion(3L, EstadoHabitacion.OCUPADA)));
        when(reservaRepository.obtenerOcupacionMensual()).thenReturn(List.of());

        EstadisticasGeneralesDTO estadisticas = dashboardService.obtenerEstadisticasGenerales();

        assertThat(estadisticas.getTotalIngresos()).isEqualByComparingTo("5000.00");
        assertThat(estadisticas.getTotalIngresosMes()).isEqualByComparingTo("500.00");
        assertThat(estadisticas.getTotalReservas()).isEqualTo(10L);
        assertThat(estadisticas.getTotalReservasConfirmadas()).isEqualTo(2L);
        assertThat(estadisticas.getTotalHabitaciones()).isEqualTo(5L);
        assertThat(estadisticas.getHabitacionesDisponibles()).isEqualTo(2L);
        assertThat(estadisticas.getTasaOcupacionPromedio()).isZero();
    }

    @Test
    @DisplayName("estadisticas: convierte valores nulos de la base de datos en cero")
    void estadisticasManejaNulos() {
        when(reservaRepository.obtenerTotalIngresos()).thenReturn(null);
        when(reservaRepository.obtenerIngresosPorMesYAño(anyInt(), anyInt())).thenReturn(null);
        when(reservaRepository.count()).thenReturn(0L);
        when(reservaRepository.findByEstado(EstadoReserva.CONFIRMADA)).thenReturn(List.of());
        when(habitacionRepository.count()).thenReturn(0L);
        when(habitacionRepository.findAll()).thenReturn(List.of());
        when(reservaRepository.obtenerOcupacionMensual()).thenReturn(List.of());

        EstadisticasGeneralesDTO estadisticas = dashboardService.obtenerEstadisticasGenerales();

        assertThat(estadisticas.getTotalIngresos()).isEqualByComparingTo("0");
        assertThat(estadisticas.getTotalIngresosMes()).isEqualByComparingTo("0");
        assertThat(estadisticas.getTasaOcupacionPromedio()).isZero();
    }

    @Test
    @DisplayName("ocupacion mensual: genera 12 meses y calcula el porcentaje de ocupacion")
    void calculaOcupacionMensual() {
        when(habitacionRepository.count()).thenReturn(2L);
        when(reservaRepository.obtenerDiasOcupadosPorMes(anyInt(), anyInt())).thenReturn(14L);

        List<OcupacionMensualDTO> ocupacion = dashboardService.obtenerOcupacionMensualUltimos12Meses();

        YearMonth actual = YearMonth.now();
        OcupacionMensualDTO ultimo = ocupacion.get(11);
        double esperado = 14.0 / (2 * actual.atEndOfMonth().getDayOfMonth()) * 100;

        assertThat(ocupacion).hasSize(12);
        assertThat(ultimo.getMes()).isEqualTo(actual.getMonthValue());
        assertThat(ultimo.getAño()).isEqualTo(actual.getYear());
        assertThat(ultimo.getHabitacionesOcupadas()).isEqualTo(14L);
        assertThat(ultimo.getTotalHabitaciones()).isEqualTo(2L);
        assertThat(ultimo.getPorcentajeOcupacion()).isCloseTo(esperado, within(0.0001));
        assertThat(ultimo.getLabel()).contains(String.valueOf(actual.getYear()));
    }

    @Test
    @DisplayName("ocupacion mensual: devuelve 0% cuando no hay habitaciones registradas")
    void ocupacionSinHabitaciones() {
        when(habitacionRepository.count()).thenReturn(0L);
        when(reservaRepository.obtenerDiasOcupadosPorMes(anyInt(), anyInt())).thenReturn(5L);

        List<OcupacionMensualDTO> ocupacion = dashboardService.obtenerOcupacionMensualUltimos12Meses();

        assertThat(ocupacion).hasSize(12).allSatisfy(item -> {
            assertThat(item.getPorcentajeOcupacion()).isZero();
            assertThat(item.getTotalHabitaciones()).isZero();
        });
    }

    @Test
    @DisplayName("ocupacion mensual: limita el porcentaje maximo a 100%")
    void ocupacionLimitaAl100PorCiento() {
        when(habitacionRepository.count()).thenReturn(1L);
        when(reservaRepository.obtenerDiasOcupadosPorMes(anyInt(), anyInt())).thenReturn(1000L);

        List<OcupacionMensualDTO> ocupacion = dashboardService.obtenerOcupacionMensualUltimos12Meses();

        assertThat(ocupacion).allSatisfy(item -> assertThat(item.getPorcentajeOcupacion()).isEqualTo(100.0));
    }

    @Test
    @DisplayName("top habitaciones: calcula el ingreso promedio y usa N/A sin tipo")
    void calculaTopHabitaciones() {
        when(reservaRepository.obtenerTopHabitacionesReservadas()).thenReturn(List.of(
                new Object[] { 1L, "HAB-101", NombreTipoHabitacion.SUITE, 2L, new BigDecimal("300.00") },
                new Object[] { 2L, "HAB-102", null, 3L, new BigDecimal("100.00") }));

        List<TopHabitacionDTO> top = dashboardService.obtenerTopHabitaciones(5);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).getCodigoHabitacion()).isEqualTo("HAB-101");
        assertThat(top.get(0).getTipoHabitacion()).isEqualTo("SUITE");
        assertThat(top.get(0).getIngresoPromedio()).isEqualByComparingTo("150.00");
        assertThat(top.get(1).getTipoHabitacion()).isEqualTo("N/A");
        assertThat(top.get(1).getIngresoPromedio()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("top habitaciones: respeta el limite solicitado")
    void topHabitacionesRespetaLimite() {
        when(reservaRepository.obtenerTopHabitacionesReservadas()).thenReturn(List.of(
                new Object[] { 1L, "HAB-101", NombreTipoHabitacion.SIMPLE, 5L, new BigDecimal("500.00") },
                new Object[] { 2L, "HAB-102", NombreTipoHabitacion.DOBLE, 3L, new BigDecimal("300.00") },
                new Object[] { 3L, "HAB-103", NombreTipoHabitacion.SUITE, 1L, new BigDecimal("200.00") }));

        List<TopHabitacionDTO> top = dashboardService.obtenerTopHabitaciones(2);

        assertThat(top).hasSize(2);
        assertThat(top).extracting(TopHabitacionDTO::getCodigoHabitacion).containsExactly("HAB-101", "HAB-102");
    }

    @Test
    @DisplayName("ocupacion por mes: calcula el porcentaje de un mes especifico")
    void calculaOcupacionPorMes() {
        when(reservaRepository.obtenerDiasOcupadosPorMes(2, 2026)).thenReturn(28L);
        when(habitacionRepository.count()).thenReturn(2L);

        OcupacionMensualDTO ocupacion = dashboardService.obtenerOcupacionPorMes(2, 2026);

        assertThat(ocupacion.getMes()).isEqualTo(2);
        assertThat(ocupacion.getAño()).isEqualTo(2026);
        assertThat(ocupacion.getLabel()).isEqualTo("Febrero 2026");
        assertThat(ocupacion.getHabitacionesOcupadas()).isEqualTo(28L);
        assertThat(ocupacion.getPorcentajeOcupacion()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("resumen: integra estadisticas, ocupacion mensual y top de habitaciones")
    void integraResumenDashboard() {
        when(reservaRepository.obtenerTotalIngresos()).thenReturn(new BigDecimal("1000.00"));
        when(reservaRepository.obtenerIngresosPorMesYAño(anyInt(), anyInt())).thenReturn(new BigDecimal("100.00"));
        when(reservaRepository.count()).thenReturn(4L);
        when(reservaRepository.findByEstado(EstadoReserva.CONFIRMADA))
                .thenReturn(List.of(reserva(EstadoReserva.CONFIRMADA)));
        when(habitacionRepository.count()).thenReturn(2L);
        when(habitacionRepository.findAll()).thenReturn(List.of(habitacion(1L, EstadoHabitacion.DISPONIBLE)));
        when(reservaRepository.obtenerOcupacionMensual()).thenReturn(List.of());
        when(reservaRepository.obtenerDiasOcupadosPorMes(anyInt(), anyInt())).thenReturn(10L);
        when(reservaRepository.obtenerTopHabitacionesReservadas()).thenReturn(List.<Object[]>of(
                new Object[] { 1L, "HAB-101", NombreTipoHabitacion.DOBLE, 2L, new BigDecimal("160.00") }));

        DashboardResumenDTO resumen = dashboardService.obtenerResumenDashboard();

        assertThat(resumen.getEstadisticasGenerales()).isNotNull();
        assertThat(resumen.getEstadisticasGenerales().getTotalReservas()).isEqualTo(4L);
        assertThat(resumen.getOcupacionMensual()).hasSize(12);
        assertThat(resumen.getTopHabitaciones()).hasSize(1);
        assertThat(resumen.getTopHabitaciones().get(0).getIngresoPromedio()).isEqualByComparingTo("80.00");
    }
}
