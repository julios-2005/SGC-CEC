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

/** Egresos persistidos: pago automático a docentes, filas históricas y filas manuales del CEC. */
@Entity
@Table(name = "informe_economico_lineas_egreso")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeEconomicoLineaEgreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_informe_economico", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InformeEconomico informeEconomico;

    @Column(name = "concepto", nullable = false, length = 100)
    private String concepto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "valor_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "manual", nullable = false)
    @Builder.Default
    private boolean manual = false;

    /**
     * Corrige para presentación el dato histórico en el que el especialista
     * quedó con valor unitario 8,00 y total 800,00 durante la migración. Para
     * los demás conceptos se conserva el valor original del Excel, incluso si
     * representa un valor global y no una tarifa por unidad.
     */
    public BigDecimal getValorUnitarioMostrar() {
        if (cantidad != null && cantidad > 0
                && concepto != null && concepto.trim().equalsIgnoreCase("ESPECIALISTA")
                && valorUnitario != null && total != null
                && valorUnitario.multiply(BigDecimal.valueOf(cantidad)).compareTo(total) != 0) {
            return total.divide(BigDecimal.valueOf(cantidad), 2, java.math.RoundingMode.HALF_UP);
        }
        return valorUnitario;
    }
}
