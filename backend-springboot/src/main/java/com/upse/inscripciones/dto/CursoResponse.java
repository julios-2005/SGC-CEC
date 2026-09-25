package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Modalidad;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursoResponse {

    private Long idCurso;
    private String nombre;
    private String codigo;
    private Integer horas;
    private BigDecimal costo;
    private Integer cuposTotales;
    private Integer cuposRestantes;
    private Modalidad modalidad;
    private EstadoCurso estado;
    private String foto;
    private com.upse.inscripciones.entity.AmbitoCurso ambito;

    public static CursoResponse fromEntity(Curso c) {
        return CursoResponse.builder()
                .idCurso(c.getIdCurso())
                .nombre(c.getNombre())
                .codigo(c.getCodigo())
                .horas(c.getHoras())
                .costo(c.getCosto())
                .cuposTotales(c.getCuposTotales())
                .cuposRestantes(c.getCuposRestantes())
                .modalidad(c.getModalidad())
                .estado(c.getEstado())
                .foto(c.getFoto())
                .ambito(c.getAmbito())
                .build();
    }
}
