package com.upse.inscripciones.config;

import com.upse.inscripciones.entity.Descuento;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.repository.DescuentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Siembra los 7 descuentos por tipo de participante la primera vez que se
 * levanta la app (solo si la tabla "descuentos" está vacía). Corridas
 * posteriores no hacen nada, así que es seguro dejarlo siempre activo.
 * Los porcentajes se pueden editar luego desde la pantalla de Descuentos.
 */
@Component
@RequiredArgsConstructor
@Order(1)
public class DatosInicialesRunner implements CommandLineRunner {

    private final DescuentoRepository descuentoRepository;

    private record DescuentoInicial(TipoUsuario tipo, String nombre, String porcentaje) {}

    private static final List<DescuentoInicial> DESCUENTOS_INICIALES = List.of(
            new DescuentoInicial(TipoUsuario.EXTERNO, "EXTERNO", "0"),
            new DescuentoInicial(TipoUsuario.ESTUDIANTE_UPSE, "ESTUDIANTE UPSE", "50"),
            new DescuentoInicial(TipoUsuario.DOCENTE_UPSE, "DOCENTE UPSE", "25"),
            new DescuentoInicial(TipoUsuario.ADMINISTRATIVO_UPSE, "ADMINISTRATIVO UPSE", "25"),
            new DescuentoInicial(TipoUsuario.MAESTRANDO, "MAESTRANDO", "0"),
            new DescuentoInicial(TipoUsuario.GRADUADO, "GRADUADO UPSE -3 NIVEL", "25"),
            new DescuentoInicial(TipoUsuario.PERSONA_DISCAPACIDAD, "PERSONA CON DISCAPACIDAD", "30")
    );

    @Override
    public void run(String... args) {
        if (descuentoRepository.count() > 0) {
            return;
        }
        for (DescuentoInicial d : DESCUENTOS_INICIALES) {
            descuentoRepository.save(Descuento.builder()
                    .nombre(d.nombre())
                    .tipoUsuario(d.tipo())
                    .porcentaje(new BigDecimal(d.porcentaje()))
                    .estado(true)
                    .build());
        }
    }
}
