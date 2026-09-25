package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.EstadoInscripcion;
import com.upse.inscripciones.entity.Inscripcion;
import com.upse.inscripciones.entity.Sexo;
import com.upse.inscripciones.entity.TipoUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscripcionResponse {

    private Long id;
    private TipoUsuario tipoUsuario;
    private String nombreCompleto;
    private PlanificacionInfo planificacion;   // ✅ Objeto anidado (antes era String curso)
    private String cedula;
    private String telefono;
    private String correoElectronico;
    private String direccion;
    private Sexo sexo;
    private String comprobantePago;
    private String copiaCedula;
    private String documentoAdicional;
    private String comprobanteMatriculaPdf;
    private LocalDateTime fechaRegistro;
    private EstadoInscripcion estado;   // ✅ NUEVO: PENDIENTE / ACEPTADA / RECHAZADA

    // ✅ DTO INTERNO PARA PLANIFICACION (incluye el nombre del curso)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanificacionInfo {
        private Long id;
        private String nombreCurso;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String horario;
    }

    public static InscripcionResponse fromEntity(Inscripcion i) {
        return InscripcionResponse.builder()
                .id(i.getId())
                .tipoUsuario(i.getTipoUsuario())
                .nombreCompleto(i.getNombreCompleto())
                .planificacion(PlanificacionInfo.builder()
                        .id(i.getPlanificacion().getId())
                        .nombreCurso(i.getPlanificacion().getCurso().getNombre())
                        .fechaInicio(i.getPlanificacion().getFechaInicio())
                        .fechaFin(i.getPlanificacion().getFechaFin())
                        .horario(i.getPlanificacion().getHorario())
                        .build())
                .cedula(i.getCedula())
                .telefono(i.getTelefono())
                .correoElectronico(i.getCorreoElectronico())
                .direccion(i.getDireccion())
                .sexo(i.getSexo())
                .comprobantePago(i.getComprobantePago())
                .copiaCedula(i.getCopiaCedula())
                .documentoAdicional(i.getDocumentoAdicional())
                .comprobanteMatriculaPdf(i.getComprobanteMatriculaPdf())
                .fechaRegistro(i.getFechaRegistro())
                .estado(i.getEstado())
                .build();
    }
}
