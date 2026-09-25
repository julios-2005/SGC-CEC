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

/**
 * Detalle de ingresos por tipo de participante (una fila por cada valor de
 * TipoUsuario) de un InformeEconomico. Se borra y se vuelve a generar
 * completo cada vez que se recalcula el informe de esa planificación.
 */
@Entity
@Table(name = "informe_economico_lineas_ingreso")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeEconomicoLineaIngreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_informe_economico", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InformeEconomico informeEconomico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", length = 30)
    private TipoUsuario tipoUsuario;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "valor_unitario", precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /**
     * Etiqueta original de la línea en los informes HISTÓRICOS migrados desde
     * Excel (p. ej. "DESCUENTO GRUPAL 1", "GRATIS", "RETIRADOS", "UPSE"),
     * cuando la categoría del Excel no coincide 1:1 con un TipoUsuario del
     * sistema. Si es null (caso de todos los informes calculados por la
     * aplicación), en pantalla y en el Excel exportado se muestra la
     * descripción normal del TipoUsuario.
     */
    @Column(name = "etiqueta", length = 50)
    private String etiqueta;

    /**
     * true = la cantidad de participantes de esta línea fue editada a mano
     * desde la pantalla del Informe Económico. Cuando está en true, el
     * recálculo automático (InformeEconomicoService#recalcularYGuardar)
     * respeta esa cantidad en vez de sobrescribirla con el conteo de
     * inscripciones aceptadas. El valor por participante NUNCA se edita a
     * mano: sigue saliendo del costo del curso y del descuento del tipo de
     * participante, y el total de la línea siempre es cantidad × valor.
     */
    @Column(name = "cantidad_manual", nullable = false)
    @Builder.Default
    private boolean cantidadManual = false;

    /** Texto a mostrar como detalle de la línea: la etiqueta original si existe, o la descripción del tipo. */
    public String getDetalleMostrar() {
        return (etiqueta != null && !etiqueta.isBlank()) ? etiqueta : tipoUsuario == null ? "Sin categoría especificada" : tipoUsuario.getDescripcion();
    }
}
