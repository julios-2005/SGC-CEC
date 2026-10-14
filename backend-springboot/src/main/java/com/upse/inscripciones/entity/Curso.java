package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cursos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_curso")
    private Long idCurso;

    // 255 (antes 150): los nombres de los diplomados históricos migrados desde
    // el informe económico en Excel superan los 150 caracteres.
    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    // Código único del curso (ej. "009-02421"), usado por el estudiante como
    // referencia/detalle al hacer la transferencia bancaria del pago.
    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "horas", nullable = false)
    private Integer horas;

    @Column(name = "costo", nullable = false, precision = 10, scale = 2)
    private BigDecimal costo;

    @Column(name = "cupos_totales", nullable = false)
    private Integer cuposTotales;

    @Column(name = "cupos_restantes", nullable = false)
    private Integer cuposRestantes;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false, length = 20)
    private Modalidad modalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoCurso estado = EstadoCurso.EN_ESPERA;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    // Foto opcional del curso, se muestra en el catálogo público (cursos-disponibles.html).
    // En blanco si no se ha subido ninguna.
    @Column(name = "foto")
    private String foto;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambito", length = 20)
    private AmbitoCurso ambito;

    @PrePersist
    public void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoCurso.EN_ESPERA;
        }
    }
}
