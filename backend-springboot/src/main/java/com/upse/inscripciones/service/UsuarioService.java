package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.RestablecerContrasenaResponse;
import com.upse.inscripciones.dto.UsuarioRequest;
import com.upse.inscripciones.dto.UsuarioResponse;
import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.UsuarioRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final CoordinadorRepository coordinadorRepository;
    private final AuthService authService;

    /**
     * Obtiene todos los usuarios
     */
    public List<UsuarioResponse> obtenerTodos() {
        return usuarioRepository.findAll().stream()
                .map(this::convertirAResponse)
                .collect(Collectors.toList());
    }

    // ✅ NUEVO: versión paginada para UsuarioController#obtenerTodos
    // (pantalla de gestión, usuarios.html), ahora con buscador (nombre de
    // usuario, nombre completo o nombre del coordinador asociado) y
    // filtros de rol/estado. Mismo patrón con Specification que
    // CursoService#listarTodos, para evitar el bug de Hibernate con bind
    // de parámetro null + Pageable.
    public Page<UsuarioResponse> obtenerTodos(String texto, Rol rol, Boolean estado, Pageable pageable) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();
        Sort.Order ordenNombre = pageable.getSort().getOrderFor("nombreCompleto");

        Specification<Usuario> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            Join<Usuario, Coordinador> coordinadorJoin = textoNormalizado != null || ordenNombre != null
                    ? root.join("coordinador", JoinType.LEFT) : null;
            if (ordenNombre != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                // Ordena por el nombre que realmente muestra la interfaz, también para coordinadores.
                var nombreCoordinador = cb.concat(cb.concat(coordinadorJoin.<String>get("nombres"), " "), coordinadorJoin.<String>get("apellidos"));
                var nombrePropio = cb.coalesce(cb.nullif(cb.trim(root.<String>get("nombreCompleto")), ""), root.<String>get("nombreUsuario"));
                var nombreMostrar = cb.<String>selectCase().when(cb.isNotNull(coordinadorJoin.get("id")), nombreCoordinador).otherwise(nombrePropio);
                query.orderBy(ordenNombre.isAscending() ? cb.asc(cb.lower(nombreMostrar)) : cb.desc(cb.lower(nombreMostrar)), cb.asc(root.get("id")));
            }
            if (rol != null) {
                condiciones.add(cb.equal(root.get("rol"), rol));
            }
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (textoNormalizado != null) {
                String patron = "%" + textoNormalizado + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombreUsuario")), patron),
                        cb.like(cb.lower(root.get("nombreCompleto")), patron),
                        cb.like(cb.lower(coordinadorJoin.get("nombres")), patron),
                        cb.like(cb.lower(coordinadorJoin.get("apellidos")), patron)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };

        Pageable paginacion = ordenNombre == null ? pageable : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return usuarioRepository.findAll(spec, paginacion)
                .map(this::convertirAResponse);
    }

    /**
     * Obtiene un usuario por ID
     */
    public UsuarioResponse obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return convertirAResponse(usuario);
    }

    /**
     * Obtiene un usuario por nombreUsuario
     */
    public UsuarioResponse obtenerPorNombreUsuario(String nombreUsuario) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario == null ? null : nombreUsuario.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return convertirAResponse(usuario);
    }

    /**
     * Crea un nuevo usuario con contraseña temporal.
     * <p>
     * La necesidad (o no) de un Coordinador depende del rol:
     * COORDINADOR necesita uno válido y sin otra cuenta ya asociada
     * (relación 1:1); ADMIN_GENERAL puede opcionalmente vincularse a un
     * coordinador existente (para heredar su nombre/foto en pantalla) o
     * quedarse sin uno; el resto de roles (ej. COBROS) nunca lleva
     * coordinador.
     */
    public UsuarioResponse crearUsuario(UsuarioRequest request) {
        String nombreUsuario = request.getNombreUsuario().trim();
        if (usuarioRepository.existsByNombreUsuario(nombreUsuario)) {
            throw new ValidacionException("El usuario '" + nombreUsuario + "' ya existe");
        }

        Rol rol = parsearRol(request.getRol());

        Coordinador coordinador = null;
        String nombreCompleto = null;

        if (rol == Rol.COORDINADOR) {
            coordinador = resolverCoordinadorObligatorio(request.getIdCoordinador(), null);
        } else if (rol == Rol.ADMIN_GENERAL) {
            // Vínculo opcional: si se manda idCoordinador, se valida igual
            // que para COORDINADOR (existente, activo, sin otra cuenta ya
            // asociada); si no se manda, el admin queda sin coordinador.
            coordinador = resolverCoordinadorOpcional(request.getIdCoordinador(), null);
            nombreCompleto = coordinador == null ? nombreCompletoOAusente(request.getNombreCompleto()) : null;
        } else {
            // Otros roles futuros sin coordinador (ej. COBROS).
            nombreCompleto = nombreCompletoOAusente(request.getNombreCompleto());
        }

        String contraseñaTemporal = generarContraseñaTemporal();

        Usuario usuario = Usuario.builder()
                .nombreUsuario(nombreUsuario)
                .contraseña(authService.encriptarContraseña(contraseñaTemporal))
                .coordinador(coordinador)
                .nombreCompleto(nombreCompleto)
                .rol(rol)
                .estado(request.getEstado() != null ? request.getEstado() : true)
                .debeCambiarContraseña(true) // Obliga a cambiar contraseña en primer login
                .build();

        usuario = usuarioRepository.save(usuario);

        UsuarioResponse response = convertirAResponse(usuario);
        // Agregar contraseña temporal a la respuesta para mostrar al admin
        response.setContraseñaTemporal(contraseñaTemporal);

        return response;
    }

    /**
     * Actualiza un usuario: rol, estado, y la asociación con Coordinador
     * cuando corresponda al nuevo rol.
     * <p>
     * Transiciones:
     * - a ADMIN_GENERAL: el coordinador es opcional — si se manda
     *   idCoordinador se valida y se vincula (igual que COORDINADOR); si
     *   no se manda, se limpia cualquier vínculo previo y se puede
     *   actualizar nombreCompleto en el mismo request. Si queda vinculado
     *   a un coordinador, nombreCompleto se ignora (el nombre del
     *   coordinador manda, ver Usuario.getNombreParaMostrar()).
     * - a COORDINADOR: exige idCoordinador válido y sin otra cuenta ya
     *   asociada (excluyendo al propio usuario que se está editando, para
     *   permitir guardar sin cambios o reasignar a otro coordinador).
     * - a cualquier otro rol (ej. COBROS): se limpia el coordinador, sin
     *   posibilidad de vincularlo.
     */
    public UsuarioResponse actualizarUsuario(Long id, UsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        String nombreUsuario = request.getNombreUsuario().trim();
        if (!usuario.getNombreUsuario().equals(nombreUsuario)
                && usuarioRepository.existsByNombreUsuario(nombreUsuario)) {
            throw new ValidacionException("El usuario '" + nombreUsuario + "' ya existe");
        }

        Rol nuevoRol = parsearRol(request.getRol());

        if (nuevoRol == Rol.COORDINADOR) {
            Coordinador coordinador = resolverCoordinadorObligatorio(request.getIdCoordinador(), id);
            usuario.setCoordinador(coordinador);
            usuario.setNombreCompleto(null);
        } else if (nuevoRol == Rol.ADMIN_GENERAL) {
            Coordinador coordinador = resolverCoordinadorOpcional(request.getIdCoordinador(), id);
            usuario.setCoordinador(coordinador);
            if (coordinador != null) {
                usuario.setNombreCompleto(null);
            } else if (request.getNombreCompleto() != null) {
                usuario.setNombreCompleto(nombreCompletoOAusente(request.getNombreCompleto()));
            }
        } else {
            // Transición a cualquier otro rol sin coordinador (ej. COBROS):
            // se limpia la asociación previa, si la tenía.
            usuario.setCoordinador(null);
            if (request.getNombreCompleto() != null) {
                usuario.setNombreCompleto(nombreCompletoOAusente(request.getNombreCompleto()));
            }
        }

        usuario.setRol(nuevoRol);
        usuario.setNombreUsuario(nombreUsuario);
        if (request.getEstado() != null) {
            usuario.setEstado(request.getEstado());
        }
        usuario = usuarioRepository.save(usuario);

        return convertirAResponse(usuario);
    }

    /**
     * Desactiva un usuario
     */
    public UsuarioResponse desactivarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        usuario.setEstado(false);
        usuario = usuarioRepository.save(usuario);

        return convertirAResponse(usuario);
    }

    /**
     * Activa un usuario
     */
    public UsuarioResponse activarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        usuario.setEstado(true);
        usuario = usuarioRepository.save(usuario);

        return convertirAResponse(usuario);
    }

    /**
     * Restablece la contraseña de un usuario con temporal
     */
    public RestablecerContrasenaResponse restablecerContraseña(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        String contraseñaTemporal = generarContraseñaTemporal();
        usuario.setContraseña(authService.encriptarContraseña(contraseñaTemporal));
        usuario.setDebeCambiarContraseña(true);
        usuarioRepository.save(usuario);

        return RestablecerContrasenaResponse.builder()
                .exito(true)
                .mensaje("Contraseña restablecida exitosamente")
                .contraseñaTemporal(contraseñaTemporal)
                .build();
    }

    /**
     * Elimina un usuario de forma permanente (borrado físico, sin retorno).
     * El coordinador asociado NO se elimina, solo el registro de acceso.
     */
    public void eliminarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        usuarioRepository.delete(usuario);
    }

    /**
     * Convierte el String del request a Rol, con mensaje de error claro.
     */
    private Rol parsearRol(String rolTexto) {
        try {
            return Rol.valueOf(rolTexto.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Rol inválido: " + rolTexto);
        }
    }

    /**
     * Busca y valida un Coordinador para un usuario con rol COORDINADOR:
     * debe existir, y no debe tener ya otra cuenta asociada.
     *
     * @param idUsuarioActual id del usuario que se está editando (para
     *                        excluirlo de la validación de duplicado al
     *                        actualizar), o null si es una creación nueva.
     */
    private Coordinador resolverCoordinadorObligatorio(Long idCoordinador, Long idUsuarioActual) {
        if (idCoordinador == null) {
            throw new ValidacionException("Debes seleccionar un coordinador para el rol COORDINADOR.");
        }

        Coordinador coordinador = coordinadorRepository.findById(idCoordinador)
                .orElseThrow(() -> new RecursoNoEncontradoException("Coordinador no encontrado"));

        if (!Boolean.TRUE.equals(coordinador.getEstado())) {
            throw new ValidacionException("El coordinador seleccionado no está activo.");
        }

        usuarioRepository.findByCoordinadorId(idCoordinador)
                .filter(otroUsuario -> !otroUsuario.getId().equals(idUsuarioActual))
                .ifPresent(otroUsuario -> {
                    throw new ValidacionException(
                            "El coordinador " + coordinador.getNombres() + " ya tiene un usuario asignado");
                });

        return coordinador;
    }

    /**
     * Igual que {@link #resolverCoordinadorObligatorio}, pero para roles
     * (hoy solo ADMIN_GENERAL) donde el coordinador es opcional: si
     * idCoordinador es null, simplemente no hay vínculo (retorna null) en
     * vez de lanzar error. Si se manda un id, se valida exactamente igual
     * (existente, activo, sin otra cuenta ya asociada) — un admin
     * vinculado a un coordinador respeta la misma relación 1:1.
     */
    private Coordinador resolverCoordinadorOpcional(Long idCoordinador, Long idUsuarioActual) {
        if (idCoordinador == null) {
            return null;
        }
        return resolverCoordinadorObligatorio(idCoordinador, idUsuarioActual);
    }

    /**
     * Normaliza nombreCompleto: recorta espacios y trata el blanco como
     * ausente, para no guardar cadenas vacías en la base de datos.
     */
    private String nombreCompletoOAusente(String nombreEnviado) {
        return (nombreEnviado != null && !nombreEnviado.isBlank()) ? nombreEnviado.trim() : null;
    }

    private static final SecureRandom RANDOM_SEGURO = new SecureRandom();

    // Alfabeto sin caracteres ambiguos al leerlos en pantalla o dictarlos
    // por teléfono: sin 0/O, sin 1/I/L.
    private static final String ALFABETO_CONTRASEÑA_TEMPORAL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * Genera una contraseña temporal con formato CEC-XXXXXXXX.
     * <p>
     * Usa {@link SecureRandom} (no System.currentTimeMillis()) porque el
     * valor anterior era predecible: derivaba del reloj del sistema en el
     * instante exacto de la petición.
     * <p>
     * El sufijo antes eran solo 4 dígitos (0000-9999, 10.000 combinaciones),
     * con el prefijo "CEC-" fijo y público (visible en el propio código y
     * en cualquier captura del panel admin). Eso lo dejaba dentro de un
     * rango de fuerza bruta razonable si un atacante conocía el nombre de
     * usuario, aun con el rate limiter de LoginRateLimiterService. Ahora
     * son 8 caracteres alfanuméricos (33^8 ≈ 1.7 billones de combinaciones).
     * <p>
     * Se garantiza al menos un dígito en el sufijo (y no solo confiar en el
     * sorteo aleatorio) porque AuthService.validarFormatoContraseña exige
     * como mínimo una mayúscula y un número; sin esta garantía, había una
     * probabilidad no despreciable (~11%) de generar un sufijo compuesto
     * solo por letras y que el alta del usuario fallara con un error de
     * validación inesperado para el admin.
     */
    private String generarContraseñaTemporal() {
        StringBuilder sufijo = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sufijo.append(ALFABETO_CONTRASEÑA_TEMPORAL.charAt(
                    RANDOM_SEGURO.nextInt(ALFABETO_CONTRASEÑA_TEMPORAL.length())));
        }
        if (sufijo.chars().noneMatch(Character::isDigit)) {
            int posicion = RANDOM_SEGURO.nextInt(sufijo.length());
            String digitos = "23456789";
            sufijo.setCharAt(posicion, digitos.charAt(RANDOM_SEGURO.nextInt(digitos.length())));
        }
        return "CEC-" + sufijo;
    }

    /**
     * Convierte Usuario a UsuarioResponse. Null-safe respecto al
     * coordinador: cuando el usuario no tiene coordinador asociado (ej.
     * ADMIN_GENERAL), todos los campos coordinador* quedan en null en vez
     * de lanzar NullPointerException.
     */
    private UsuarioResponse convertirAResponse(Usuario usuario) {
        Coordinador coordinador = usuario.getCoordinador();

        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nombreUsuario(usuario.getNombreUsuario())
                .rol(usuario.getRol().name())
                .estado(usuario.getEstado())
                .nombre(usuario.getNombreParaMostrar())
                .foto(usuario.getFotoParaMostrar())
                .coordinadorId(coordinador != null ? coordinador.getId() : null)
                .coordinadorNombre(coordinador != null ? coordinador.getNombres() : null)
                .coordinadorApellido(coordinador != null ? coordinador.getApellidos() : null)
                .coordinadorFoto(coordinador != null ? coordinador.getFoto() : null)
                .coordinadorCorreo(coordinador != null ? coordinador.getCorreo() : null)
                .build();
    }
}