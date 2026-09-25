package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.*;
import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.repository.InformeEconomicoRepository;
import com.upse.inscripciones.repository.InscripcionRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * El Informe Económico se guarda en PostgreSQL (tablas "informes_economicos",
 * "informe_economico_lineas_ingreso" e "informe_economico_lineas_egreso") y
 * se recalcula/sobrescribe automáticamente cada vez que ocurre algo que lo
 * afecta:
 * <ul>
 *   <li>Cambia el estado de una inscripción de esa planificación
 *       (InscripcionService#cambiarEstado)</li>
 *   <li>Se registra una Planificación nueva, o se le edita el costoEspecialista
 *       (PlanificacionService)</li>
 *   <li>Se edita el costo de un Curso (CursoService#actualizar) → se
 *       recalculan todas las planificaciones de ese curso</li>
 *   <li>Se registra/edita/activa/desactiva/elimina un Descuento
 *       (DescuentoService) → se recalculan TODOS los informes, porque un
 *       descuento por tipo de participante puede afectar a varias
 *       planificaciones a la vez</li>
 * </ul>
 * La pantalla (informe-economico.html, vía InformeEconomicoController) lee
 * siempre directo de estas tablas: no hay cálculo "al vuelo" en las
 * consultas, así se garantiza que lo que se ve en pantalla es exactamente
 * lo mismo que hay guardado en la base de datos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InformeEconomicoService {

    private record NombreProgramacion(String nombreVisible, String nombreBase, int numero) {}
    private record InformeNombrado(InformeEconomico informe, NombreProgramacion nombre) {}

    private final PlanificacionRepository planificacionRepository;
    private final InscripcionRepository inscripcionRepository;
    private final InformeEconomicoRepository informeEconomicoRepository;
    private final DescuentoService descuentoService;

    // ── Listado (resumen) ────────────────────────────────────────────────────

    public List<InformeEconomicoResumenResponse> listarResumen() {
        return listarResumen(null, null, null, null);
    }

    // ✅ NUEVO: con filtros opcionales de texto (nombre del curso), modalidad
    // y rango de fecha de inicio. La lista completa de informes es pequeña
    // (una fila por planificación, no por inscripción), así que se filtra
    // en memoria sobre el resultado ya traído con JOIN FETCH, en vez de
    // armar un Specification adicional solo para esto.
    @Transactional
    public List<InformeEconomicoResumenResponse> listarResumen(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta) {
        return filtrarInformes(texto, modalidad, fechaDesde, fechaHasta).stream()
                .map(this::mapearResumen)
                .sorted(Comparator
                        .comparing((InformeEconomicoResumenResponse r) -> nombreBase(r.getNombreCurso()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(r -> numeroProgramacion(r.getNombreCurso()))
                        .thenComparing(InformeEconomicoResumenResponse::getFechaInicio,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(InformeEconomicoResumenResponse::getIdInforme,Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<InformeNombrado> filtrarInformes(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();
        List<InformeEconomico> todos = informeEconomicoRepository.findAllConDetalles();
        Map<Long, NombreProgramacion> nombres = construirNombresProgramacion(todos);

        return todos.stream()
                .filter(i -> i.getPlanificacion() != null
                        || !Boolean.TRUE.equals(i.getHistorico())
                        || (i.getAnioReferencia() != null && !i.isExcluido2026()))
                .map(informe -> new InformeNombrado(informe,
                        nombres.getOrDefault(informe.getClaveReporte(),
                                nombreSinProgramacion(informe))))
                .filter(item -> {
                    InformeEconomico informe = item.informe();
                    if (modalidad != null && informe.getModalidadReporte() != modalidad) {
                        return false;
                    }
                    if (fechaDesde != null && (informe.getInicioReporte()==null || informe.getInicioReporte().isBefore(fechaDesde))) {
                        return false;
                    }
                    if (fechaHasta != null && (informe.getInicioReporte()==null || informe.getInicioReporte().isAfter(fechaHasta))) {
                        return false;
                    }
                    if (textoNormalizado != null
                            && !item.nombre().nombreVisible().toLowerCase().contains(textoNormalizado)) {
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    // ── Exportar a Excel (InformeEconomicoExcelService) ─────────────────────
    // Devuelve las ENTIDADES (no DTOs) porque el Excel necesita, además de los
    // totales, cada línea de ingreso/egreso individual (una fila por
    // TipoUsuario, igual que en pantalla). Se fuerza la carga de todo lo LAZY
    // (lineasIngreso, lineasEgreso, especialista, coordinador) mientras la
    // transacción sigue abierta, para que InformeEconomicoExcelService pueda
    // leerlas después sin LazyInitializationException.
    @Transactional(readOnly = true)
    public List<InformeEconomico> listarParaExportar(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta) {
        List<InformeNombrado> encontrados = filtrarInformes(texto, modalidad, fechaDesde, fechaHasta);
        List<InformeEconomico> informes = encontrados.stream().map(item -> {
            item.informe().setNombreReporte(item.nombre().nombreVisible());
            return item.informe();
        }).collect(Collectors.toCollection(ArrayList::new));

        // Mismo orden que ve el usuario en la pantalla de resumen, para que la
        // hoja resumen del Excel liste los cursos en ese mismo orden.
        informes.sort(Comparator
                .comparing((InformeEconomico ie) -> nombreBase(ie.getNombreReporte()), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ie -> numeroProgramacion(ie.getNombreReporte()))
                .thenComparing(InformeEconomico::getInicioReporte,Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(InformeEconomico::getClaveReporte));

        for (InformeEconomico informe : informes) {
            informe.getLineasIngreso().size();
            informe.getLineasEgreso().size();
            informe.getDocentesReporte();informe.getCoordinadorReporte();
        }
        return informes;
    }

    private InformeEconomicoResumenResponse mapearResumen(InformeNombrado item) {
        InformeEconomico informe = item.informe();
        Planificacion planificacion = informe.getPlanificacion();
        BigDecimal egresos = informe.getEgresosTotal();
        BigDecimal utilidad = informe.getIngresosTotal().subtract(egresos);
        return InformeEconomicoResumenResponse.builder()
                .idPlanificacion(planificacion==null?null:planificacion.getId())
                .idInforme(informe.getId()).anio(informe.getAnioReporte()).cifrasPendientes(informe.isCifrasPendientes())
                .excluido2026(informe.isExcluido2026()).observacion(informe.getObservacionReferencia())
                .nombreCurso(item.nombre().nombreVisible())
                .modalidad(informe.getModalidadReporte())
                .fechaInicio(informe.getInicioReporte())
                .participantes(informe.getParticipantes())
                .ingresos(informe.getIngresosTotal())
                .egresos(egresos)
                .utilidad(utilidad)
                .build();
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    @Transactional
    public InformeEconomicoDetalleResponse obtenerDetalle(Long idPlanificacion) {
        InformeEconomico informe = idPlanificacion<0 ? informeEconomicoRepository.findById(-idPlanificacion)
                .filter(i->i.getPlanificacion()==null).orElseThrow(()->new RecursoNoEncontradoException("Referencia económica no encontrada."))
                : informeEconomicoRepository.findByPlanificacionId(idPlanificacion)
                // Defensivo: si por algún motivo todavía no existe (planificación muy
                // recién creada, o dato previo a esta funcionalidad), se genera ahora.
                .orElseGet(() -> {
                    recalcularYGuardar(idPlanificacion);
                    return informeEconomicoRepository.findByPlanificacionId(idPlanificacion)
                            .orElseThrow(() -> new RecursoNoEncontradoException(
                                    "No se encontró la planificación con id " + idPlanificacion));
                });

        Planificacion planificacion = informe.getPlanificacion();
        Map<Long, NombreProgramacion> nombres = construirNombresProgramacion(
                informeEconomicoRepository.findAllConDetalles());
        String nombreVisible = nombres.getOrDefault(informe.getClaveReporte(), nombreSinProgramacion(informe))
                .nombreVisible();

        List<IngresoLineaResponse> ingresos = informe.getLineasIngreso().stream()
                .map(linea -> IngresoLineaResponse.builder()
                        .id(linea.getId())
                        .tipoUsuario(linea.getTipoUsuario())
                        .detalle(linea.getDetalleMostrar())
                        .cantidad(linea.getCantidad())
                        .valorUnitario(linea.getValorUnitario())
                        .total(linea.getTotal())
                        // Solo se puede editar la cantidad si la línea tiene un valor por
                        // participante definido: sin ese valor no habría con qué multiplicar.
                        .editable(linea.getValorUnitario() != null && !informe.isCifrasPendientes())
                        .cantidadManual(linea.isCantidadManual())
                        .build())
                .sorted(Comparator.comparing(l -> l.getTipoUsuario()==null?Integer.MAX_VALUE:l.getTipoUsuario().ordinal()))
                .toList();

        List<EgresoLineaResponse> egresos = informe.getLineasEgreso().stream()
                .map(linea -> EgresoLineaResponse.builder()
                        .id(linea.getId())
                        .concepto(linea.getConcepto())
                        .cantidad(linea.getCantidad())
                        .valorUnitario(linea.getValorUnitarioMostrar())
                        .total(linea.getTotal())
                        .editable(linea.isManual())
                        .build())
                .toList();

        return InformeEconomicoDetalleResponse.builder()
                .idPlanificacion(planificacion==null?null:planificacion.getId())
                .idInforme(informe.getId()).anio(informe.getAnioReporte()).cifrasPendientes(informe.isCifrasPendientes())
                .excluido2026(informe.isExcluido2026()).observacion(informe.getObservacionReferencia())
                .nombreCurso(nombreVisible)
                .modalidad(informe.getModalidadReporte()==null?null:informe.getModalidadReporte().name())
                .especialista(informe.getDocentesReporte()).coordinador(informe.getCoordinadorReporte())
                .fechaInicio(informe.getInicioReporte())
                .fechaFin(informe.getFinReporte())
                .participantes(informe.getParticipantes())
                .ingresos(ingresos)
                .ingresosTotal(informe.getIngresosTotal())
                .egresos(egresos)
                .egresosTotal(informe.getEgresosTotal())
                .utilidad(informe.getUtilidad())
                .build();
    }

    // ── Nombre de la programación ───────────────────────────────────────────

    private Map<Long, NombreProgramacion> construirNombresProgramacion(List<InformeEconomico> informes) {
        Map<Long, List<InformeEconomico>> porCurso = informes.stream().filter(i->i.getPlanificacion()!=null)
                .collect(Collectors.groupingBy(i -> i.getPlanificacion().getCurso().getIdCurso()));
        Map<Long, NombreProgramacion> resultado = new HashMap<>();

        for (List<InformeEconomico> grupo : porCurso.values()) {
            grupo.sort(Comparator
                    .comparing((InformeEconomico i) -> i.getPlanificacion().getFechaInicio())
                    .thenComparing(i -> i.getPlanificacion().getId()));
            boolean variasProgramaciones = grupo.size() > 1;
            for (int indice = 0; indice < grupo.size(); indice++) {
                InformeEconomico informe = grupo.get(indice);
                String base = informe.getPlanificacion().getCurso().getNombre().trim();
                int numero = variasProgramaciones ? indice + 1 : 0;
                String visible = variasProgramaciones
                        ? base + " - " + etiquetaProgramacion(numero)
                        : base;
                resultado.put(informe.getPlanificacion().getId(),
                        new NombreProgramacion(visible, base, numero));
            }
        }
        // Solo los informes SIN planificación real (históricos puros,
        // migrados del Excel) usan el nombre congelado tal cual. Los que sí
        // tienen planificación ya quedaron en "resultado" arriba con el
        // nombre EN VIVO del curso (más el sufijo "- Primera programación"
        // etc. si corresponde) — no se deben pisar con nombreReferencia,
        // o el informe mostraría un nombre desactualizado apenas alguien
        // edite el curso en Gestión de Cursos.
        for(var i:informes)if(i.getPlanificacion()==null)
            resultado.put(i.getClaveReporte(),nombreSinProgramacion(i));
        return resultado;
    }

    private NombreProgramacion nombreSinProgramacion(InformeEconomico informe) {
        String base = informe.getNombreBaseReporte().trim();
        return new NombreProgramacion(base, base, 0);
    }

    private String etiquetaProgramacion(int numero) {
        return switch (numero) {
            case 1 -> "Primera programación";
            case 2 -> "Segunda programación";
            case 3 -> "Tercera programación";
            case 4 -> "Cuarta programación";
            case 5 -> "Quinta programación";
            case 6 -> "Sexta programación";
            case 7 -> "Séptima programación";
            case 8 -> "Octava programación";
            case 9 -> "Novena programación";
            case 10 -> "Décima programación";
            case 11 -> "Undécima programación";
            case 12 -> "Duodécima programación";
            case 13 -> "Decimotercera programación";
            case 14 -> "Decimocuarta programación";
            case 15 -> "Decimoquinta programación";
            case 16 -> "Decimosexta programación";
            case 17 -> "Decimoséptima programación";
            case 18 -> "Decimoctava programación";
            case 19 -> "Decimonovena programación";
            case 20 -> "Vigésima programación";
            default -> "Programación N.º " + numero;
        };
    }

    private String nombreBase(String nombreVisible) {
        int separador = nombreVisible.lastIndexOf(" - ");
        return separador >= 0 ? nombreVisible.substring(0, separador) : nombreVisible;
    }

    private int numeroProgramacion(String nombreVisible) {
        String[] ordinales = {"Primera", "Segunda", "Tercera", "Cuarta", "Quinta", "Sexta", "Séptima",
                "Octava", "Novena", "Décima", "Undécima", "Duodécima", "Decimotercera", "Decimocuarta",
                "Decimoquinta", "Decimosexta", "Decimoséptima", "Decimoctava", "Decimonovena", "Vigésima"};
        for (int i = 0; i < ordinales.length; i++) {
            if (nombreVisible.contains(" - " + ordinales[i] + " programación")) {
                return i + 1;
            }
        }
        int marcador = nombreVisible.lastIndexOf("Programación N.º ");
        if (marcador >= 0) {
            try {
                return Integer.parseInt(nombreVisible.substring(marcador + "Programación N.º ".length()).trim());
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }
        return 0;
    }

    // ── Recalculo y persistencia ─────────────────────────────────────────────

    /** Recalcula y guarda (crea o sobrescribe) el informe de UNA planificación. */
    @Transactional
    public void recalcularYGuardar(Long idPlanificacion) {
        Planificacion planificacion = planificacionRepository.findByIdForUpdate(idPlanificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la planificación con id " + idPlanificacion));

        InformeEconomico informe = informeEconomicoRepository.findByPlanificacionId(idPlanificacion)
                .orElseGet(() -> InformeEconomico.builder()
                        .planificacion(planificacion)
                        .lineasIngreso(new ArrayList<>())
                        .lineasEgreso(new ArrayList<>())
                        .build());

        // Los informes HISTÓRICOS (migrados desde el Excel de cursos ya
        // dictados) no tienen Inscripciones reales en el sistema: recalcular
        // ingresos/participantes desde cero los dejaría en cero, así que esa
        // parte se conserva tal cual fue migrada o editada a mano en pantalla.
        // El EGRESO (costo del especialista) es distinto: no depende de
        // Inscripciones, depende del campo costoEspecialista de esta misma
        // Planificación, que sí es un dato vivo y editable desde
        // Planificaciones — por eso se recalcula SIEMPRE, sea o no histórico
        // el informe, para que un cambio de costo se refleje de inmediato.
        boolean esHistorico = Boolean.TRUE.equals(informe.getHistorico());

        if (esHistorico) {
            // Un histórico conserva sus ingresos y sus egresos ya informados.
            // Si todavía no tiene egresos (por ejemplo, un informe histórico
            // recién creado por una prueba/migración), se inicializa una sola
            // vez con el costo vivo de la planificación, que es el comportamiento
            // esperado para ese caso. Después, los cambios manuales se conservan
            // y recalcularTodo no los reemplaza.
            if (informe.getEgresosTotal() == null) {
                var pagos = HonorariosEspecialistas.desglosar(planificacion);
                BigDecimal pagoDocentes = pagos.isEmpty() ? obtenerCostoEspecialista(planificacion)
                        : pagos.stream().map(HonorariosEspecialistas.Pago::pagoNeto).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal adicionales = informe.getLineasEgreso().stream()
                        .filter(InformeEconomicoLineaEgreso::isManual)
                        .map(InformeEconomicoLineaEgreso::getTotal)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                informe.setEgresosTotal(pagoDocentes.add(adicionales));
            }
            BigDecimal ingresos = informe.getIngresosTotal() == null ? BigDecimal.ZERO : informe.getIngresosTotal();
            BigDecimal egresos = informe.getEgresosTotal() == null ? BigDecimal.ZERO : informe.getEgresosTotal();
            informe.setUtilidad(ingresos.subtract(egresos));
            informeEconomicoRepository.saveAndFlush(informe);
            return;
        }

        if (!esHistorico) {
            List<Inscripcion> aceptadas = inscripcionRepository.findAceptadasByPlanificacionId(idPlanificacion);

            // Cantidades de participantes editadas a mano en la pantalla del
            // informe: se respetan al recalcular (si no, el recálculo
            // automático de cada arranque las borraría). El VALOR por
            // participante sí se vuelve a calcular siempre, para que siga
            // reflejando el costo del curso y el descuento vigente de ese
            // tipo de participante.
            Map<TipoUsuario, Integer> cantidadesManuales = informe.getLineasIngreso().stream()
                    .filter(l -> l.isCantidadManual() && l.getTipoUsuario() != null)
                    .collect(Collectors.toMap(InformeEconomicoLineaIngreso::getTipoUsuario,
                            InformeEconomicoLineaIngreso::getCantidad, (a, b) -> a));

            List<InformeEconomicoLineaIngreso> lineas = calcularLineasIngreso(planificacion, aceptadas, cantidadesManuales);
            BigDecimal ingresosTotal = lineas.stream()
                    .map(InformeEconomicoLineaIngreso::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Suma de las cantidades de todas las líneas: sin ediciones
            // manuales da exactamente el total de inscripciones aceptadas,
            // igual que antes.
            informe.setParticipantes(lineas.stream().mapToInt(InformeEconomicoLineaIngreso::getCantidad).sum());
            informe.setIngresosTotal(ingresosTotal);

            // Reemplaza por completo las líneas de ingreso viejas por las
            // nuevas (orphanRemoval = true en la entidad se encarga de
            // borrar las que sobran).
            informe.getLineasIngreso().clear();
            for (InformeEconomicoLineaIngreso linea : lineas) {
                linea.setInformeEconomico(informe);
                informe.getLineasIngreso().add(linea);
            }
        }

        // pago.pagoNeto() = honorario bruto + transferencia (costo TOTAL para
        // el CEC de cada especialista extranjero; para uno local, es igual
        // al honorario porque no hay transferencia que sumar).
        {
            var pagos = HonorariosEspecialistas.desglosar(planificacion);
            BigDecimal pagoDocentes = pagos.isEmpty() ? obtenerCostoEspecialista(planificacion)
                    : pagos.stream().map(HonorariosEspecialistas.Pago::pagoNeto).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal adicionales = informe.getLineasEgreso().stream().filter(InformeEconomicoLineaEgreso::isManual)
                    .map(InformeEconomicoLineaEgreso::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal egresosTotal = pagoDocentes.add(adicionales);
            informe.setEgresosTotal(egresosTotal);
            informe.setUtilidad(informe.getIngresosTotal().subtract(egresosTotal));

            // Renueva el pago automático y conserva los egresos ingresados manualmente.
            informe.getLineasEgreso().removeIf(linea -> !linea.isManual());
            if(pagos.isEmpty())agregarPago(informe,"Especialistas (honorario total)",pagoDocentes);
            else for(var pago:pagos){
                agregarPago(informe,"Especialista: "+pago.nombre(),pago.honorario());
                if(pago.costoTransferencia().signum()>0)agregarPago(informe,"Costo de transferencia (25%): "+pago.nombre(),pago.costoTransferencia());
            }
        }

        informeEconomicoRepository.saveAndFlush(informe);
    }

    // ── Edición manual de la cantidad de participantes (ingresos) ───────────

    /**
     * Cambia a mano la cantidad de participantes de UNA línea de ingreso. El
     * valor por participante de esa línea no se toca: sigue siendo el que
     * corresponde a su tipo de participante (costo del curso menos su
     * descuento). El total de la línea se vuelve a calcular como
     * cantidad × valor, y el TOTAL INGRESOS del informe se ajusta sumando
     * solo la diferencia, de modo que cada línea que se edita va sumando al
     * total definitivo sin recalcular ni alterar las demás.
     */
    @Transactional
    public InformeEconomicoDetalleResponse guardarIngreso(Long idPlanificacion, Long idLinea, IngresoRequest r) {
        var informe = informeParaEditar(idPlanificacion);
        if (informe.isCifrasPendientes()) throw new com.upse.inscripciones.exception.ValidacionException(
                "Esta referencia del Excel no tiene cifras informadas. Completa su respaldo antes de editar los ingresos.");
        var linea = lineaIngreso(informe, idLinea);
        if (linea.getValorUnitario() == null) throw new com.upse.inscripciones.exception.ValidacionException(
                "Esta fila no tiene un valor por participante definido, así que su cantidad no se puede editar.");

        int cantidadAnterior = linea.getCantidad() == null ? 0 : linea.getCantidad();
        BigDecimal totalAnterior = linea.getTotal() == null ? BigDecimal.ZERO : linea.getTotal();
        BigDecimal total = linea.getValorUnitario().multiply(BigDecimal.valueOf(r.cantidad()));
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new com.upse.inscripciones.exception.ValidacionException(
                    "El total de la línea de ingreso supera el máximo permitido.");

        linea.setCantidad(r.cantidad());
        linea.setTotal(total);
        linea.setCantidadManual(true);
        aplicarVariacionIngresos(informe, total.subtract(totalAnterior), r.cantidad() - cantidadAnterior);
        informeEconomicoRepository.saveAndFlush(informe);
        return obtenerDetalle(idPlanificacion);
    }

    /**
     * Deshace la edición manual de una línea: vuelve a tomar la cantidad del
     * conteo de inscripciones aceptadas. En las referencias del Excel y en
     * los informes históricos (que no tienen inscripciones en el sistema)
     * simplemente se quita la marca de "editado" y la cantidad se conserva.
     */
    @Transactional
    public InformeEconomicoDetalleResponse restablecerIngreso(Long idPlanificacion, Long idLinea) {
        var informe = informeParaEditar(idPlanificacion);
        var linea = lineaIngreso(informe, idLinea);
        linea.setCantidadManual(false);
        informeEconomicoRepository.saveAndFlush(informe);
        if (informe.getPlanificacion() != null && !Boolean.TRUE.equals(informe.getHistorico())) {
            recalcularYGuardar(informe.getPlanificacion().getId());
        }
        return obtenerDetalle(idPlanificacion);
    }

    private InformeEconomicoLineaIngreso lineaIngreso(InformeEconomico informe, Long id) {
        return informe.getLineasIngreso().stream().filter(l -> l.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Línea de ingreso no encontrada en este reporte."));
    }

    private void aplicarVariacionIngresos(InformeEconomico informe, BigDecimal variacion, int variacionParticipantes) {
        // Igual que en los egresos: se aplica solo la diferencia, sin
        // reconstruir cifras históricas ni tocar las demás líneas.
        BigDecimal ingresos = informe.getIngresosTotal().add(variacion);
        if (ingresos.signum() < 0 || ingresos.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new com.upse.inscripciones.exception.ValidacionException("El total de ingresos no es válido.");
        int participantes = (informe.getParticipantes() == null ? 0 : informe.getParticipantes()) + variacionParticipantes;
        if (participantes < 0) participantes = 0;
        informe.setIngresosTotal(ingresos);
        informe.setParticipantes(participantes);
        informe.setUtilidad(ingresos.subtract(informe.getEgresosTotal()));
    }

    @Transactional
    public InformeEconomicoDetalleResponse guardarEgreso(Long idPlanificacion, Long idLinea, EgresoRequest r) {
        var informe = informeParaEditar(idPlanificacion);
        if(informe.isCifrasPendientes()) throw new com.upse.inscripciones.exception.ValidacionException("Esta referencia del Excel no tiene cifras informadas. Completa su respaldo antes de añadir egresos.");
        var linea = idLinea == null ? InformeEconomicoLineaEgreso.builder()
                .informeEconomico(informe).manual(true).build() : lineaManual(informe, idLinea);
        BigDecimal anterior = idLinea == null ? BigDecimal.ZERO : linea.getTotal();
        BigDecimal total = r.valorUnitario().multiply(BigDecimal.valueOf(r.cantidad()));
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new com.upse.inscripciones.exception.ValidacionException("El total del egreso supera el máximo permitido.");
        linea.setConcepto(r.concepto().trim());
        linea.setCantidad(r.cantidad());
        linea.setValorUnitario(r.valorUnitario());
        linea.setTotal(total);
        if (idLinea == null) informe.getLineasEgreso().add(linea);
        aplicarVariacionEgresos(informe, total.subtract(anterior));
        informeEconomicoRepository.saveAndFlush(informe);
        return obtenerDetalle(idPlanificacion);
    }

    @Transactional
    public InformeEconomicoDetalleResponse eliminarEgreso(Long idPlanificacion, Long idLinea) {
        var informe = informeParaEditar(idPlanificacion);
        var linea = lineaManual(informe, idLinea);
        aplicarVariacionEgresos(informe, linea.getTotal().negate());
        informe.getLineasEgreso().remove(linea);
        informeEconomicoRepository.saveAndFlush(informe);
        return obtenerDetalle(idPlanificacion);
    }

    private InformeEconomico informeParaEditar(Long id) {
        if(id<0)return informeEconomicoRepository.findByIdForUpdate(-id).filter(i->i.getPlanificacion()==null)
                .orElseThrow(()->new RecursoNoEncontradoException("Referencia económica no encontrada."));
        planificacionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Planificación no encontrada."));
        return informeEconomicoRepository.findByPlanificacionId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Informe no encontrado."));
    }

    private InformeEconomicoLineaEgreso lineaManual(InformeEconomico informe, Long id) {
        var linea = informe.getLineasEgreso().stream().filter(l -> l.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Egreso no encontrado en este reporte."));
        if (!linea.isManual()) throw new com.upse.inscripciones.exception.ValidacionException(
                "Esta fila es histórica o automática. El pago de docentes nuevos se edita en Planificaciones.");
        return linea;
    }

    private void aplicarVariacionEgresos(InformeEconomico informe, BigDecimal variacion) {
        // Aplica únicamente el cambio solicitado, sin reconstruir cifras históricas.
        BigDecimal egresos = informe.getEgresosTotal().add(variacion);
        if (egresos.signum() < 0 || egresos.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new com.upse.inscripciones.exception.ValidacionException("El total de egresos no es válido.");
        informe.setEgresosTotal(egresos);
        informe.setUtilidad(informe.getIngresosTotal().subtract(egresos));
    }

    /** Recalcula TODAS las planificaciones. Usado cuando cambia un Descuento (afecta a varias a la vez). */
    @Transactional
    public void recalcularTodo() {
        for (Planificacion planificacion : planificacionRepository.findAll()) {
            recalcularYGuardar(planificacion.getId());
        }
    }

    /** Recalcula solo las planificaciones de un Curso. Usado cuando cambia el costo de ese curso. */
    @Transactional
    public void recalcularPorCurso(Long idCurso) {
        for (Planificacion planificacion : planificacionRepository.findByCursoId(idCurso)) {
            recalcularYGuardar(planificacion.getId());
        }
    }

    // ── Cálculo de ingresos (agrupado por tipo de participante) ────────────────

    private List<InformeEconomicoLineaIngreso> calcularLineasIngreso(Planificacion planificacion,
            List<Inscripcion> aceptadas, Map<TipoUsuario, Integer> cantidadesManuales) {
        BigDecimal costoCurso = planificacion.getCurso().getCosto();

        Map<TipoUsuario, Long> conteoPorTipo = aceptadas.stream()
                .collect(Collectors.groupingBy(Inscripcion::getTipoUsuario, Collectors.counting()));

        List<InformeEconomicoLineaIngreso> lineas = new ArrayList<>();
        for (TipoUsuario tipo : TipoUsuario.values()) {
            Integer manual = cantidadesManuales.get(tipo);
            long cantidad = manual != null ? manual : conteoPorTipo.getOrDefault(tipo, 0L);
            BigDecimal porcentaje = descuentoService.obtenerPorcentajePara(tipo);
            BigDecimal valorUnitario = costoCurso
                    .multiply(BigDecimal.ONE.subtract(porcentaje.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = valorUnitario.multiply(BigDecimal.valueOf(cantidad));

            lineas.add(InformeEconomicoLineaIngreso.builder()
                    .tipoUsuario(tipo)
                    .cantidad((int) cantidad)
                    .valorUnitario(valorUnitario)
                    .total(total)
                    .cantidadManual(manual != null)
                    .build());
        }
        return lineas;
    }

    // ── Egreso: costo del especialista ──────────────────────────────────

    private void agregarPago(InformeEconomico informe,String concepto,BigDecimal importe){
        informe.getLineasEgreso().add(InformeEconomicoLineaEgreso.builder().informeEconomico(informe)
                .concepto(concepto.length()>100?concepto.substring(0,100):concepto).cantidad(1).valorUnitario(importe).total(importe).build());
    }
    private BigDecimal obtenerCostoEspecialista(Planificacion planificacion) {
        return planificacion.getCostoEspecialista() != null ? planificacion.getCostoEspecialista() : BigDecimal.ZERO;
    }
}
