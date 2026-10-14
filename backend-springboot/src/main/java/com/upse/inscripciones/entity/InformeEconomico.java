package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Copia persistida (en PostgreSQL) del Informe Económico de una Planificación.
 * Existe una fila por Planificación (relación 1 a 1). Se recalcula y
 * sobrescribe automáticamente cada vez que ocurre algo que afecta el
 * cálculo: cambia el estado de una inscripción de esa planificación, se
 * edita el costo del curso, el costo del especialista, o el porcentaje de un
 * descuento vigente. Ver InformeEconomicoService#recalcularYGuardar.
 */
@Entity
@Table(name = "informes_economicos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeEconomico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY a propósito: InformeEconomicoRepository#findAllConDetalles trae
    // con JOIN FETCH lo que InformeEconomicoResumenResponse necesita
    // (planificacion + curso), en vez de cargarlo siempre.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_planificacion", unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Planificacion planificacion;
    @Column(name="referencia_excel",unique=true,length=120) private String referenciaExcel;
    @Column(name="nombre_referencia",length=500) private String nombreReferencia;
    @Column(name="anio_referencia") private Integer anioReferencia;
    @Column(name="fecha_inicio_referencia") private java.time.LocalDate fechaInicioReferencia;
    @Column(name="fecha_fin_referencia") private java.time.LocalDate fechaFinReferencia;
    @Column(name="docente_referencia",length=300) private String docenteReferencia;
    @Column(name="coordinador_referencia",length=300) private String coordinadorReferencia;
    @Column(name="observacion_referencia",length=500) private String observacionReferencia;
    @Column(name="cifras_pendientes",nullable=false) @Builder.Default private boolean cifrasPendientes=false;
    @Column(name="excluido_2026",nullable=false) @Builder.Default private boolean excluido2026=false;
    public int getAnioReporte(){return anioReferencia!=null?anioReferencia:planificacion.getFechaInicio().getYear();}
    public java.time.LocalDate getInicioReporte(){return planificacion==null?fechaInicioReferencia:planificacion.getFechaInicio();}
    public java.time.LocalDate getFinReporte(){return planificacion==null?fechaFinReferencia:planificacion.getFechaFin();}
    // Si hay una planificación real vinculada, el nombre en vivo del Curso
    // (el que se edita en Gestión de Cursos) siempre gana sobre el texto
    // congelado de nombreReferencia — así el Informe Económico nunca queda
    // desactualizado respecto a Gestión de Cursos/Planificaciones. La
    // referencia congelada solo aplica a informes históricos sin
    // planificación en el sistema (migrados directo del Excel).
    public String getNombreBaseReporte(){return planificacion!=null?planificacion.getCurso().getNombre():nombreReferencia;}
    public String getDocentesReporte(){return planificacion==null?docenteReferencia:planificacion.getNombresDocentes();}
    public String getCoordinadorReporte(){return planificacion==null?coordinadorReferencia:planificacion.getCoordinador().getNombres()+" "+planificacion.getCoordinador().getApellidos();}
    public Modalidad getModalidadReporte(){return planificacion==null?null:planificacion.getModalidad();}
    public Long getClaveReporte(){return planificacion==null?-id:planificacion.getId();}


    @Column(name = "participantes", nullable = false)
    private Integer participantes;

    @Column(name = "ingresos_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal ingresosTotal;

    @Column(name = "egresos_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal egresosTotal;

    @Column(name = "utilidad", nullable = false, precision = 12, scale = 2)
    private BigDecimal utilidad;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    /**
     * true = informe HISTÓRICO, migrado desde los informes económicos en Excel
     * de cursos ya dictados (no tiene inscripciones reales en el sistema).
     * Estos informes NUNCA se recalculan automáticamente: si se recalcularan,
     * el cálculo a partir de las inscripciones (que no existen) los dejaría
     * en cero. Ver InformeEconomicoService#recalcularYGuardar.
     */
    @Column(name = "historico", nullable = false)
    @Builder.Default
    private Boolean historico = false;

    /**
     * Nombre listo para presentar o exportar. En los cursos que tienen varias
     * ediciones incluye "Primera programación", "Segunda programación", etc.
     * Es transitorio porque se deriva de las planificaciones y no modifica el
     * esquema de la base de datos existente.
     */
    @Transient
    private String nombreReporte;

    @OneToMany(mappedBy = "informeEconomico", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<InformeEconomicoLineaIngreso> lineasIngreso = new ArrayList<>();

    @OneToMany(mappedBy = "informeEconomico", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<InformeEconomicoLineaEgreso> lineasEgreso = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void actualizarFecha() {
        fechaActualizacion = LocalDateTime.now();
    }
}
