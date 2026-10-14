package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "gastos_cec")
@Data
@NoArgsConstructor
public class GastoCec {
    @Id
    private Integer anio;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe = BigDecimal.ZERO;
    @Version
    private Long version;
}
