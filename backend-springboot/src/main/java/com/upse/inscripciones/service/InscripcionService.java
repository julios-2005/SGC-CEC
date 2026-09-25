package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.InscripcionResponse;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.EstadoInscripcion;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Inscripcion;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.entity.Sexo;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.CursoRepository;
import com.upse.inscripciones.repository.InscripcionRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import com.upse.inscripciones.util.TelefonoUtils;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InscripcionService {

    private final InscripcionRepository inscripcionRepository;
    private final PlanificacionRepository planificacionRepository;
    private final CursoRepository cursoRepository;
    private final FileStorageService fileStorageService;
    private final ComprobanteMatriculaPdfService comprobanteMatriculaPdfService;
    private final EmailService emailService;
    private final InformeEconomicoService informeEconomicoService;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    // Cédula ecuatoriana (10 dígitos) o número de pasaporte (alfanumérico,
    // 5 a 20 caracteres) para permitir la inscripción de extranjeros.
    private static final Pattern CEDULA_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern PASAPORTE_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,20}$");

    @Transactional
    public InscripcionResponse registrarInscripcion(
            TipoUsuario tipoUsuario,
            String nombreCompleto,
            Long idPlanificacion,
            String cedula,
            String telefono,
            String correoElectronico,
            String direccion,
            Sexo sexo,
            MultipartFile comprobantePago,
            MultipartFile copiaCedula,
            MultipartFile documentoAdicional) {

        validarCamposBasicos(tipoUsuario, nombreCompleto, idPlanificacion, cedula, telefono, correoElectronico, direccion, sexo);
        cedula = cedula.trim();
        // Se normaliza a formato internacional E.164 (+593...) para poder
        // generar enlaces de WhatsApp de forma confiable más adelante.
        String telefonoNormalizado = TelefonoUtils.normalizar(telefono);

        // Coincide con el índice único parcial real de la base de datos
        // (ver InscripcionRepository.existsByCedulaAndPlanificacionId): un
        // mismo estudiante puede inscribirse en varios cursos/planificaciones
        // distintas, y también puede volver a inscribirse en la MISMA
        // planificación si su inscripción anterior fue RECHAZADA o RETIRADA;
        // solo se bloquea si ya tiene una PENDIENTE o ACEPTADA para ella.
        if (inscripcionRepository.existsByCedulaAndPlanificacionId(cedula, idPlanificacion)) {
            throw new ValidacionException(
                    "Ya existe una inscripción registrada con la cédula " + cedula + " para este curso.");
        }

        Planificacion planificacion = planificacionRepository.findById(idPlanificacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la planificación con id: " + idPlanificacion));

        Curso curso = planificacion.getCurso();
        // Se fuerza la inicialización del proxy de Curso ANTES del UPDATE de
        // abajo: decrementarCupoSiHayDisponible usa @Modifying(clearAutomatically
        // = true), que vacía el contexto de persistencia (para evitar leer
        // datos obsoletos) y con eso desconecta a "planificacion" y a este
        // proxy de "curso". Si no se inicializa antes, InscripcionResponse
        // .fromEntity() revienta más abajo con LazyInitializationException
        // al intentar leer curso.getNombre() sin sesión activa. Una vez que
        // el proxy ya cargó sus datos reales, sigue siendo legible aunque
        // después quede detached (Hibernate ya no necesita volver a la BD).
        org.hibernate.Hibernate.initialize(curso);
        validarDisponibilidadParaNuevaInscripcion(planificacion, curso);
        // UPDATE atómico y condicional (ver CursoRepository): evita que dos
        // inscripciones simultáneas con 1 solo cupo restante pasen ambas la
        // validación antes de que cualquiera confirme su escritura.
        int filasAfectadas = cursoRepository.decrementarCupoSiHayDisponible(curso.getIdCurso());
        if (filasAfectadas == 0) {
            throw new ValidacionException(
                    "No hay cupos disponibles para el curso \"" + curso.getNombre() + "\".");
        }

        String rutaComprobantePago = null;
        String rutaCopiaCedula = null;

        // Documento de respaldo: obligatorio para todos, EXCEPTO Externo.
        // Su significado exacto (comprobante de matrícula, carnet de
        // discapacidad, etc.) depende del tipo de usuario elegido.
        //
        // A partir de aquí, si algo falla (ej. el tercer documento no pasa
        // la validación de Tika), los dos archivos ya escritos en disco
        // arriba quedarían huérfanos: @Transactional revierte la base de
        // datos, pero no el filesystem. Por eso el resto del método queda
        // envuelto en un try/catch que los limpia antes de relanzar el error.
        String rutaDocumentoAdicional = null;
        try {
            // También limpia el primer documento si falla la validación del segundo.
            rutaComprobantePago = fileStorageService.guardarArchivo(
                    comprobantePago, "comprobantes-pago", "Comprobante de Pago", cedula, nombreCompleto);
            rutaCopiaCedula = fileStorageService.guardarArchivo(
                    copiaCedula, "copias-cedula", "Copia de Cédula", cedula, nombreCompleto);
            if (tipoUsuario.requiereDocumentoAdicional()) {
                if (tipoUsuario.esDocumentoAdicionalOpcional()) {
                    // Docente/Administrativo Upse: se guarda si lo adjuntan, pero no es obligatorio.
                    rutaDocumentoAdicional = fileStorageService.guardarArchivoOpcional(
                            documentoAdicional, "documentos-adicionales", tipoUsuario.getEtiquetaDocumento(),
                            cedula, nombreCompleto);
                } else {
                    rutaDocumentoAdicional = fileStorageService.guardarArchivo(
                            documentoAdicional, "documentos-adicionales", tipoUsuario.getEtiquetaDocumento(),
                            cedula, nombreCompleto);
                }
            }

            Inscripcion inscripcion = Inscripcion.builder()
                    .tipoUsuario(tipoUsuario)
                    .nombreCompleto(nombreCompleto.trim())
                    .planificacion(planificacion)
                    .cedula(cedula.trim())
                    .telefono(telefonoNormalizado)
                    .correoElectronico(correoElectronico.trim().toLowerCase())
                    .direccion(direccion.trim())
                    .sexo(sexo)
                    .comprobantePago(rutaComprobantePago)
                    .copiaCedula(rutaCopiaCedula)
                    .documentoAdicional(rutaDocumentoAdicional)
                    .build();

            Inscripcion guardada = inscripcionRepository.save(inscripcion);
            return InscripcionResponse.fromEntity(guardada);
        } catch (DataIntegrityViolationException e) {
            // El chequeo de existsByCedulaAndPlanificacionId de arriba ya
            // cubre el caso normal; esto solo se dispara si dos peticiones
            // casi simultáneas pasaron ambas ese chequeo antes de que
            // cualquiera confirmara su escritura (ver índice único parcial
            // en InscripcionRepository). Damos el mismo mensaje específico en
            // vez de dejar que el GlobalExceptionHandler devuelva el
            // genérico de "conflicto con datos existentes".
            fileStorageService.eliminarSiExiste(rutaComprobantePago);
            fileStorageService.eliminarSiExiste(rutaCopiaCedula);
            fileStorageService.eliminarSiExiste(rutaDocumentoAdicional);
            throw new ValidacionException(
                    "Ya existe una inscripción registrada con la cédula " + cedula + " para este curso.");
        } catch (RuntimeException e) {
            fileStorageService.eliminarSiExiste(rutaComprobantePago);
            fileStorageService.eliminarSiExiste(rutaCopiaCedula);
            fileStorageService.eliminarSiExiste(rutaDocumentoAdicional);
            throw e;
        }
    }

    /**
     * Listado paginado de inscripciones para InscripcionController#listarTodas.
     * "texto" filtra por nombre, cédula o nombre de curso (se normaliza acá:
     * recortado y en minúsculas, o null si viene vacío). "estado" filtra por
     * estado exacto, o null para "todos los estados".
     * <p>
     * Ver el comentario equivalente en CursoService#listarTodos: se arma con
     * Specification en vez del antiguo "@Query con (:param IS NULL OR campo
     * = :param)" porque ese patrón, combinado con Pageable, disparaba un
     * bug de Hibernate (bind de parámetro null contra un enum rompía la
     * derivación de la query de COUNT) y terminaba en 500 apenas se listaba
     * sin pasar los dos filtros a la vez. El join a planificacion/curso se
     * mantiene como "fetch" (igual que el JOIN FETCH original) solo para la
     * query de datos, no para la de COUNT, para no duplicar el join ahí
     * donde no hace falta.
     */
    @SuppressWarnings("unchecked") // por el cast Fetch -> Join explicado abajo
    public Page<InscripcionResponse> listarTodas(String texto, EstadoInscripcion estado, Pageable pageable) {
        return listarTodas(texto, estado, null, null, null, null, pageable);
    }

    // ✅ NUEVO: además de texto/estado, filtra opcionalmente por tipo de
    // usuario, por una planificación puntual, y por rango de fecha de
    // registro. Mismo Specification de arriba, ampliado.
    @SuppressWarnings("unchecked") // por el cast Fetch -> Join explicado abajo
    public Page<InscripcionResponse> listarTodas(
            String texto, EstadoInscripcion estado, com.upse.inscripciones.entity.TipoUsuario tipoUsuario,
            Long idPlanificacion, java.time.LocalDateTime fechaRegistroDesde, java.time.LocalDateTime fechaRegistroHasta,
            Pageable pageable) {
        return inscripcionRepository.findAll(
                        construirSpecification(texto, estado, tipoUsuario, idPlanificacion, fechaRegistroDesde, fechaRegistroHasta),
                        pageable)
                .map(InscripcionResponse::fromEntity);
    }

    // La misma Specification sirve para el listado paginado (donde también
    // arma la query de COUNT) y para el export sin paginar (que nunca
    // ejecuta esa rama porque findAll(spec, sort) no hace COUNT).
    private Specification<Inscripcion> construirSpecification(
            String texto, EstadoInscripcion estado, com.upse.inscripciones.entity.TipoUsuario tipoUsuario,
            Long idPlanificacion, java.time.LocalDateTime fechaRegistroDesde, java.time.LocalDateTime fechaRegistroHasta) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();

        return (root, query, cb) -> {
            boolean esQueryDeConteo = query.getResultType() == Long.class || query.getResultType() == long.class;

            Join<Inscripcion, Planificacion> planificacionJoin;
            Join<Planificacion, Curso> cursoJoin;
            if (esQueryDeConteo) {
                planificacionJoin = root.join("planificacion", JoinType.INNER);
                cursoJoin = planificacionJoin.join("curso", JoinType.INNER);
            } else {
                planificacionJoin = (Join<Inscripcion, Planificacion>)
                        (Join) root.fetch("planificacion", JoinType.INNER);
                cursoJoin = (Join<Planificacion, Curso>)
                        (Join) planificacionJoin.fetch("curso", JoinType.INNER);
            }

            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (tipoUsuario != null) {
                condiciones.add(cb.equal(root.get("tipoUsuario"), tipoUsuario));
            }
            if (idPlanificacion != null) {
                condiciones.add(cb.equal(planificacionJoin.get("id"), idPlanificacion));
            }
            if (fechaRegistroDesde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaRegistro"), fechaRegistroDesde));
            }
            if (fechaRegistroHasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaRegistro"), fechaRegistroHasta));
            }
            if (textoNormalizado != null) {
                String patron = "%" + textoNormalizado + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombreCompleto")), patron),
                        cb.like(root.get("cedula"), patron),
                        cb.like(cb.lower(cursoJoin.get("nombre")), patron)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    // ✅ NUEVO: para el botón "Exportar a Excel" de inscripciones.html.
    @SuppressWarnings("unchecked")
    public List<InscripcionResponse> listarParaExportar(
            String texto, EstadoInscripcion estado, com.upse.inscripciones.entity.TipoUsuario tipoUsuario,
            Long idPlanificacion, java.time.LocalDateTime fechaRegistroDesde, java.time.LocalDateTime fechaRegistroHasta) {
        return inscripcionRepository.findAll(
                        construirSpecification(texto, estado, tipoUsuario, idPlanificacion, fechaRegistroDesde, fechaRegistroHasta),
                        org.springframework.data.domain.Sort.by("fechaRegistro").descending())
                .stream()
                .map(InscripcionResponse::fromEntity)
                .toList();
    }

    public InscripcionResponse obtenerPorId(Long id) {
        Inscripcion inscripcion = inscripcionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la inscripción con id " + id));
        return InscripcionResponse.fromEntity(inscripcion);
    }

    public List<InscripcionResponse> listarPorPlanificacion(Long idPlanificacion) {
        return inscripcionRepository.findByPlanificacionId(idPlanificacion)
                .stream()
                .map(InscripcionResponse::fromEntity)
                .toList();
    }

    @Transactional
    public InscripcionResponse cambiarEstado(Long id, EstadoInscripcion nuevoEstado) {
        if (nuevoEstado == null) {
            throw new ValidacionException("El estado es obligatorio.");
        }
        Inscripcion inscripcion = inscripcionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la inscripción con id " + id));

        EstadoInscripcion estadoAnterior = inscripcion.getEstado();

        if (estadoAnterior != nuevoEstado) {
            Curso curso = inscripcion.getPlanificacion().getCurso();
            org.hibernate.Hibernate.initialize(curso);

            boolean seLiberaCupo = ocupaCupo(estadoAnterior) && !ocupaCupo(nuevoEstado);
            boolean seOcupaCupo = !ocupaCupo(estadoAnterior) && ocupaCupo(nuevoEstado);

            if (seLiberaCupo) {
                cursoRepository.incrementarCupo(curso.getIdCurso());
            } else if (seOcupaCupo) {
                int filasAfectadas = cursoRepository.decrementarCupoSiHayDisponible(curso.getIdCurso());
                if (filasAfectadas == 0) {
                    throw new ValidacionException(
                            "No se puede cambiar el estado: ya no hay cupos disponibles para el curso \""
                                    + curso.getNombre() + "\".");
                }
            }
        }

        inscripcion.setEstado(nuevoEstado);
        Inscripcion guardada = inscripcionRepository.save(inscripcion);

        // El Informe Económico solo cuenta inscripciones ACEPTADAS, así que
        // solo hace falta recalcular cuando el cambio realmente afecta ese conteo.
        if (estadoAnterior != nuevoEstado
                && (estadoAnterior == EstadoInscripcion.ACEPTADA || nuevoEstado == EstadoInscripcion.ACEPTADA)) {
            informeEconomicoService.recalcularYGuardar(guardada.getPlanificacion().getId());
        }

        // El comprobante se genera después de que las operaciones de base de
        // datos hayan terminado correctamente. El correo se programa para
        // después del commit: nunca se notifica una aceptación que luego haya
        // sido revertida por la transacción.
        if (nuevoEstado == EstadoInscripcion.ACEPTADA && estadoAnterior != EstadoInscripcion.ACEPTADA) {
            generarYEnviarComprobante(guardada);
        }

        return InscripcionResponse.fromEntity(guardada);
    }

    private void generarYEnviarComprobante(Inscripcion inscripcion) {
        byte[] pdf = comprobanteMatriculaPdfService.generar(inscripcion);
        String nombreArchivo = inscripcion.getCedula() + "_comprobante_matricula.pdf";
        String ruta = fileStorageService.guardarBytes(pdf, "comprobantes-matricula-generados", nombreArchivo);

        inscripcion.setComprobanteMatriculaPdf(ruta);
        inscripcionRepository.save(inscripcion);

        Runnable enviarCorreo = () -> emailService.enviarComprobanteMatricula(
                inscripcion.getCorreoElectronico(),
                inscripcion.getNombreCompleto(),
                inscripcion.getPlanificacion().getCurso().getNombre(),
                pdf);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarCorreo.run();
                }
            });
        } else {
            enviarCorreo.run();
        }
    }

    private void validarDisponibilidadParaNuevaInscripcion(Planificacion planificacion, Curso curso) {
        if (planificacion.getFechaFin().isBefore(LocalDate.now())) {
            throw new ValidacionException(
                    "Esta planificación finalizó el " + planificacion.getFechaFin()
                            + " y ya no admite inscripciones.");
        }
        if (curso.getEstado() == EstadoCurso.FINALIZADO) {
            throw new ValidacionException(
                    "El curso seleccionado está finalizado y ya no admite inscripciones.");
        }
    }

    private boolean ocupaCupo(EstadoInscripcion estado) {
        return estado == EstadoInscripcion.PENDIENTE || estado == EstadoInscripcion.ACEPTADA;
    }

    private void validarCamposBasicos(TipoUsuario tipoUsuario, String nombreCompleto, Long idPlanificacion, String cedula,
                                       String telefono, String correoElectronico, String direccion, Sexo sexo) {
        if (tipoUsuario == null) {
            throw new ValidacionException("Debe seleccionar el Tipo de Usuario.");
        }
        if (nombreCompleto == null || nombreCompleto.trim().isEmpty()) {
            throw new ValidacionException("El Nombre Completo es obligatorio.");
        }
        // Mismo límite que la columna inscripciones.nombre_completo
        // varchar(150) (ver entidad Inscripcion). Antes este era el único
        // campo de texto libre del formulario público sin ningún tope de
        // longitud: llegaba sin validar hasta EmailService (ver hallazgo de
        // HTML sin escapar) y hasta Hibernate, donde un valor demasiado
        // largo producía un error genérico de "conflicto con datos
        // existentes" en vez de un mensaje claro.
        if (nombreCompleto.trim().length() > 150) {
            throw new ValidacionException("El Nombre Completo no puede superar los 150 caracteres.");
        }
        if (idPlanificacion == null) {
            throw new ValidacionException("Debe seleccionar el Curso/Planificación al que se inscribe.");
        }
        if (cedula == null
                || !(CEDULA_PATTERN.matcher(cedula.trim()).matches()
                     || PASAPORTE_PATTERN.matcher(cedula.trim()).matches())) {
            throw new ValidacionException(
                    "El Documento de Identidad debe ser una cédula de 10 dígitos o un número de pasaporte válido (5 a 20 caracteres).");
        }
        // La validación/normalización de formato internacional del teléfono
        // la hace TelefonoUtils.normalizar(); acá solo se exige que no venga vacío.
        if (telefono == null || telefono.trim().isEmpty()) {
            throw new ValidacionException("El Número de WhatsApp es obligatorio.");
        }
        if (correoElectronico == null || !EMAIL_PATTERN.matcher(correoElectronico.trim()).matches()) {
            throw new ValidacionException("El Correo Electrónico no tiene un formato válido.");
        }
        // El patrón de formato de arriba no acota la longitud (una dirección
        // "valida" según el regex puede tener miles de caracteres). Mismo
        // límite que la columna correo_electronico varchar(150).
        if (correoElectronico.trim().length() > 150) {
            throw new ValidacionException("El Correo Electrónico no puede superar los 150 caracteres.");
        }
        if (direccion == null || direccion.trim().isEmpty()) {
            throw new ValidacionException("La Dirección es obligatoria.");
        }
        if (direccion.trim().length() > 250) {
            throw new ValidacionException("La Dirección no puede superar los 250 caracteres.");
        }
        if (sexo == null) {
            throw new ValidacionException("Debe seleccionar el Sexo.");
        }
    }
}
