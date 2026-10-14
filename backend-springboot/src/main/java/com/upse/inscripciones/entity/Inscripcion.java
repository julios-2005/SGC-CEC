package com.upse.inscripciones.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
// La unicidad real de (cedula, id_planificacion) ahora vive en un índice
// único PARCIAL en la base de datos (uk_inscripcion_cedula_planificacion_activa),
// que solo aplica a inscripciones con estado PENDIENTE o ACEPTADA. Así, una
// inscripción RECHAZADA o RETIRADA ya no bloquea que la misma persona vuelva
// a inscribirse en la misma planificación. Ver InscripcionRepository
// #existsByCedulaAndPlanificacionId, que refleja la misma regla a nivel de
// aplicación.
@Table(name = "inscripciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 30)
    private TipoUsuario tipoUsuario;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    // ✅ RELACIÓN MANY-TO-ONE CON PLANIFICACION (antes era un String "curso")
    // 1 Planificacion puede tener muchas Inscripciones
    // LAZY a propósito: InscripcionRepository#findAllConDetalles trae con
    // JOIN FETCH exactamente lo que InscripcionResponse necesita
    // (planificacion + curso), en vez de cargarlo siempre.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_planificacion", nullable = false)
    private Planificacion planificacion;

    // length = 20 para admitir números de pasaporte además de la cédula de 10 dígitos
    @Column(name = "cedula", nullable = false, length = 20)
    private String cedula;

    // length = 20 para admitir el formato internacional E.164 (+593991234567)
    @Column(name = "telefono", nullable = false, length = 20)
    private String telefono;

    @Column(name = "correo_electronico", nullable = false, length = 150)
    private String correoElectronico;

    @Column(name = "direccion", nullable = false, length = 250)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", nullable = false, length = 15)
    private Sexo sexo;

    // Rutas/nombres de los archivos almacenados en disco
    @Column(name = "comprobante_pago", nullable = false)
    private String comprobantePago;

    @Column(name = "copia_cedula", nullable = false)
    private String copiaCedula;

    // Documento de respaldo según el tipo de participante (comprobante de
    // matrícula, carnet de discapacidad, nombramiento, etc.). Obligatorio
    // para todos los tipos EXCEPTO EXTERNO. Reemplaza a los antiguos
    // campos separados comprobante_matricula / carnet_discapacidad.
    @Column(name = "documento_adicional")
    private String documentoAdicional;

    // Ruta del comprobante de matrícula en PDF generado automáticamente por
    // el sistema al aceptar la inscripción (y enviado por correo).
    @Column(name = "comprobante_matricula_pdf")
    private String comprobanteMatriculaPdf;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    // ✅ NUEVO: la secretaria acepta/rechaza tras revisar los documentos
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoInscripcion estado;

    @PrePersist
    public void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoInscripcion.PENDIENTE;
        }
    }
}
