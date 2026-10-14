package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.util.Nacionalidades;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EspecialistaResponse {

    private Long id;
    private String cedula;
    private String nombres;
    private String apellidos;
    private String telefono;
    private String correo;
    private String especialidad;
    private String areaConocimiento;
    private Boolean estado;

    // Código ISO tal cual se guarda ("CO"), útil para que el frontend
    // preseleccione el <select> al editar.
    private String paisNacionalidad;
    // Nombre de país y gentilicio ya resueltos ("Colombia", "Colombiano/a"),
    // para no duplicar el catálogo de Nacionalidades en el frontend.
    private String nombrePaisNacionalidad;
    private String gentilicioNacionalidad;

    public static EspecialistaResponse fromEntity(Especialista d) {
        return EspecialistaResponse.builder()
                .id(d.getId())
                .cedula(d.getCedula())
                .nombres(d.getNombres())
                .apellidos(d.getApellidos())
                .telefono(d.getTelefono())
                .correo(d.getCorreo())
                .especialidad(d.getEspecialidad())
                .areaConocimiento(d.getAreaConocimiento())
                .estado(d.getEstado())
                .paisNacionalidad(d.getPaisNacionalidad())
                .nombrePaisNacionalidad(Nacionalidades.nombrePais(d.getPaisNacionalidad()))
                .gentilicioNacionalidad(Nacionalidades.gentilicio(d.getPaisNacionalidad()))
                .build();
    }
}
