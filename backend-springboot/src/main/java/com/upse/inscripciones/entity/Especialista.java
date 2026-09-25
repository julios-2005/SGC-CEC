package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "especialistas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Especialista {

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

    @Column(name = "especialidad", length = 100)
    private String especialidad;

    @Column(name = "area_conocimiento", length = 150)
    private String areaConocimiento;

    // Código ISO 3166-1 alpha-2 del país (ej. "EC", "CO"), no el gentilicio.
    // Ver com.upse.inscripciones.util.Nacionalidades para el porqué y para
    // convertir esto a texto ("Colombiano/a") al mostrarlo.
    @Column(name = "pais_nacionalidad", length = 5)
    private String paisNacionalidad;

    // "Activo" = está dictando clases hoy; "Disponible" = no lo está en
    // este momento. Se calcula automáticamente (ver
    // EspecialistaService#recalcularEstado); un especialista recién
    // registrado empieza en "Disponible" porque todavía no tiene ninguna
    // planificación en curso.
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private Boolean estado = false;
}
