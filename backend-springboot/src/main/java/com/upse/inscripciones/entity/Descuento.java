package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "descuentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Descuento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 50, unique = true)
    private String nombre;

    // Tipo de participante al que aplica este descuento. Reutiliza el
    // enum TipoUsuario (Externo, Estudiante Upse, etc.). Único: solo puede
    // existir un descuento por tipo de participante.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", length = 30, unique = true)
    private TipoUsuario tipoUsuario;

    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private Boolean estado = true;
}
