package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "planificaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Planificacion {

    // El campo Java se llama "id" (consistente con el resto de entidades),
    // pero se fija el nombre de columna real explícitamente porque la base
    // de datos ya existe (ddl-auto=validate) con la columna llamada
    // "id_planificaciones" — sin este @Column, Hibernate asumiría que la
    // columna se llama "id" y la validación de esquema fallaría al arrancar.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_planificaciones")
    private Long id;

    // ✅ RELACIÓN MANY-TO-ONE CON CURSO (antes era un String "actividad")
    // LAZY a propósito: quien necesite el objeto completo (listados que
    // arman PlanificacionResponse) lo trae con JOIN FETCH explícito en el
    // repositorio (ver PlanificacionRepository#findAllConDetalles), en vez
    // de cargarlo siempre aunque no haga falta.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_curso", nullable = false)
    private Curso curso;

    // ✅ RELACIÓN MANY-TO-ONE CON COORDINADOR
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_coordinador", nullable = false)
    private Coordinador coordinador;

    // ✅ RELACIÓN MANY-TO-ONE CON ESPECIALISTA
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_especialista", nullable = false)
    private Especialista especialista;

    @ManyToMany
    @JoinTable(name = "planificacion_docentes", joinColumns = @JoinColumn(name = "id_planificacion"),
            inverseJoinColumns = @JoinColumn(name = "id_especialista"))
    @OrderColumn(name = "orden")
    @Builder.Default
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private java.util.List<Especialista> docentes = new java.util.ArrayList<>();

    /** Honorario bruto por docente; nunca se distribuye automáticamente un total. */
    @ElementCollection
    @CollectionTable(name="planificacion_honorarios", joinColumns=@JoinColumn(name="id_planificacion"))
    @MapKeyColumn(name="id_especialista")
    @Column(name="honorario", nullable=false, precision=10, scale=2)
    @Builder.Default
    @lombok.ToString.Exclude @lombok.EqualsAndHashCode.Exclude
    private java.util.Map<Long,java.math.BigDecimal> honorarios = new java.util.LinkedHashMap<>();

    public java.util.List<Especialista> getDocentesEfectivos() {
        return docentes == null || docentes.isEmpty() ? java.util.List.of(especialista) : docentes;
    }

    public String getNombresDocentes() {
        return getDocentesEfectivos().stream().map(d -> d.getNombres() + " " + d.getApellidos())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "horario", nullable = false, length = 100)
    private String horario;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false, length = 20)
    private Modalidad modalidad;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    // Lo que se le paga al especialista por dictar ESTA edición del
    // curso. Vive aquí (y no en Especialista) porque un mismo especialista puede
    // cobrar distinto según el curso o la edición. Alimenta la fila fija
    // "Especialista" del Informe Económico.
    @Column(name = "costo_especialista", precision = 10, scale = 2)
    private java.math.BigDecimal costoEspecialista;

    @PrePersist
    public void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
