package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios", uniqueConstraints = {
    @UniqueConstraint(columnNames = "nombre_usuario", name = "uk_nombre_usuario")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_usuario", length = 50, nullable = false)
    private String nombreUsuario;

    @Column(name = "contrasena", length = 255, nullable = false)
    private String contraseña;

    /**
     * Nombre a mostrar cuando el usuario NO tiene coordinador asociado
     * (típicamente cuentas ADMIN_GENERAL). Opcional a propósito: si no se
     * completa, getNombreParaMostrar() cae de vuelta a nombreUsuario en
     * lugar de dejar un nombre vacío.
     */
    @Column(name = "nombre_completo", length = 150)
    private String nombreCompleto;

    /**
     * Opcional: un Usuario (cuenta de acceso) NO tiene que corresponder
     * necesariamente a un Coordinador. Son conceptos distintos: Usuario es
     * "quién puede iniciar sesión", Coordinador es "quién coordina cursos".
     * Un ADMIN_GENERAL típicamente no tiene coordinador; un usuario con rol
     * COORDINADOR sí lo tiene (ver UsuarioService para la regla completa).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_coordinador", foreignKey = @ForeignKey(name = "fk_usuarios_coordinadores"))
    private Coordinador coordinador;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", length = 20, nullable = false)
    private Rol rol;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private Boolean estado = true;

    @Column(name = "debe_cambiar_contrasena", nullable = false)
    @Builder.Default
    private Boolean debeCambiarContraseña = false;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "ultima_modificacion")
    private LocalDateTime ultimaModificacion;

    /**
     * Nombre para mostrar en pantalla (header, tabla de usuarios, sesión):
     * si tiene coordinador, usa el nombre del coordinador (fuente de verdad
     * para esos datos); si no, usa nombreCompleto; si tampoco hay eso,
     * usa nombreUsuario como último respaldo — nunca queda vacío.
     */
    @Transient
    public String getNombreParaMostrar() {
        if (coordinador != null) {
            return coordinador.getNombres() + " " + coordinador.getApellidos();
        }
        if (nombreCompleto != null && !nombreCompleto.isBlank()) {
            return nombreCompleto;
        }
        return nombreUsuario;
    }

    /**
     * Foto para mostrar: solo existe si viene de un coordinador asociado.
     * Un usuario sin coordinador simplemente no tiene foto (el frontend ya
     * muestra la inicial del nombre como avatar en ese caso).
     */
    @Transient
    public String getFotoParaMostrar() {
        return coordinador != null ? coordinador.getFoto() : null;
    }

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.ultimaModificacion = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = true;
        }
        if (this.debeCambiarContraseña == null) {
            this.debeCambiarContraseña = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.ultimaModificacion = LocalDateTime.now();
    }
}
