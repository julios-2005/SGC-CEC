package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.entity.Planificacion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanificacionResponse {

    private Long id;
    private CursoInfo curso;               // ✅ Objeto anidado (antes era String actividad)
    private CoordinadorInfo coordinador;  // ✅ Objeto anidado
    private EspecialistaInfo especialista;           // ✅ Objeto anidado
    private java.util.List<DocenteResponse> docentes;
    private java.util.List<com.upse.inscripciones.service.HonorariosEspecialistas.Pago> honorariosDocentes;

    public String getNombresDocentes() {
        return docentes == null ? "" : docentes.stream().map(d -> d.nombres() + " " + d.apellidos())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    // ✅ NUEVO: para el filtro Activas/Finalizadas y su etiqueta en
    // planificaciones.html. "Activa" = todavía no termina (fechaFin >=
    // hoy), igual que PlanificacionRepository#findVigentes; no exige que ya
    // haya empezado, para no ocultar planificaciones futuras.
    public boolean isActiva() {
        return fechaFin != null && !fechaFin.isBefore(LocalDate.now());
    }
    private LocalDate fechaInicio;
    private String horario;
    private LocalDate fechaFin;
    private Modalidad modalidad;
    private LocalDateTime fechaRegistro;
    private java.math.BigDecimal costoEspecialista;

    // ✅ DTO INTERNO PARA CURSO
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CursoInfo {
        private Long idCurso;
        private String nombre;
        private String codigo;
        private Integer horas;
        private BigDecimal costo;
        private Integer cuposRestantes;   // ✅ NUEVO: para mostrar disponibilidad en el combobox
        private String foto;              // ✅ NUEVO: catálogo público (cursos-disponibles.html)
        private com.upse.inscripciones.entity.AmbitoCurso ambito;
    }

    // ✅ DTO INTERNO PARA COORDINADOR
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CoordinadorInfo {
        private Long id;
        private String nombres;
        private String apellidos;
        private String cedula;
        private String correo;
    }

    // ✅ DTO INTERNO PARA ESPECIALISTA
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EspecialistaInfo {
        private Long id;
        private String nombres;
        private String apellidos;
        private String cedula;
        private String especialidad;
    }

    public static PlanificacionResponse fromEntity(Planificacion p) {
        return PlanificacionResponse.builder()
                .id(p.getId())
                .curso(CursoInfo.builder()
                        .idCurso(p.getCurso().getIdCurso())
                        .nombre(p.getCurso().getNombre())
                        .codigo(p.getCurso().getCodigo())
                        .horas(p.getCurso().getHoras())
                        .costo(p.getCurso().getCosto())
                        .cuposRestantes(p.getCurso().getCuposRestantes())
                        .foto(p.getCurso().getFoto())
                        .ambito(p.getCurso().getAmbito())
                        .build())
                .coordinador(CoordinadorInfo.builder()
                        .id(p.getCoordinador().getId())
                        .nombres(p.getCoordinador().getNombres())
                        .apellidos(p.getCoordinador().getApellidos())
                        .cedula(p.getCoordinador().getCedula())
                        .correo(p.getCoordinador().getCorreo())
                        .build())
                .especialista(EspecialistaInfo.builder()
                        .id(p.getEspecialista().getId())
                        .nombres(p.getEspecialista().getNombres())
                        .apellidos(p.getEspecialista().getApellidos())
                        .cedula(p.getEspecialista().getCedula())
                        .especialidad(p.getEspecialista().getEspecialidad())
                        .build())
                .fechaInicio(p.getFechaInicio())
                .docentes(p.getDocentesEfectivos().stream().map(DocenteResponse::fromEntity).toList())
                .honorariosDocentes(com.upse.inscripciones.service.HonorariosEspecialistas.desglosar(p))
                .horario(p.getHorario())
                .fechaFin(p.getFechaFin())
                .modalidad(p.getModalidad())
                .fechaRegistro(p.getFechaRegistro())
                .costoEspecialista(p.getCostoEspecialista())
                .build();
    }
}
