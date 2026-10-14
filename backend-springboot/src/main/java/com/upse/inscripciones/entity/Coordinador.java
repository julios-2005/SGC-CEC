package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coordinadores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coordinador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // length = 20 para admitir números de pasaporte además de la cédula de 10 dígitos
    @Column(name = "cedula", nullable = false, length = 20, unique = true)
    private String cedula;

    @Column(name = "nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 80)
    private String apellidos;

    // length = 20 para admitir el formato internacional E.164 (+593991234567)
    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "correo", length = 100, unique = true)
    private String correo;

    // Ruta relativa del archivo de foto (se guarda igual que los demás
    // documentos subidos, dentro de /uploads/fotos-coordinadores).
    @Column(name = "foto", length = 255)
    private String foto;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private Boolean estado = true;
}
