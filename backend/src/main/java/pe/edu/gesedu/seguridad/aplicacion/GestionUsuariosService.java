package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.seguridad.aplicacion.UsuariosService.EstadoCuenta;
import pe.edu.gesedu.seguridad.dominio.Administrador;
import pe.edu.gesedu.seguridad.dominio.Alumno;
import pe.edu.gesedu.seguridad.dominio.Apoderado;
import pe.edu.gesedu.seguridad.dominio.Docente;
import pe.edu.gesedu.seguridad.dominio.RolAcceso;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.AdministradorRepository;
import pe.edu.gesedu.seguridad.infraestructura.AlumnoRepository;
import pe.edu.gesedu.seguridad.infraestructura.ApoderadoRepository;
import pe.edu.gesedu.seguridad.infraestructura.DocenteRepository;
import pe.edu.gesedu.seguridad.infraestructura.RolAccesoRepository;
import pe.edu.gesedu.seguridad.infraestructura.TokenRecuperacionRepository;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/**
 * Alta, modificación, activación y desactivación de cuentas (UC-24, RF-01). Las cuentas nuevas de personal y de
 * apoderado nacen <b>pendientes</b>: sin contraseña, con un correo de bienvenida que lleva un enlace de 72 horas para
 * definirla (S-77). El administrador nunca define ni ve la contraseña. El alumno no se crea aquí: nace con la
 * solicitud de matrícula (UC-01) y solo se habilita, y únicamente con una matrícula activa (FA-2); el apoderado se
 * habilita sin hijos (S-82), porque necesita su cuenta antes de la primera solicitud. Los permisos se heredan del rol.
 */
@Service
public class GestionUsuariosService {

    /** Roles que se crean desde Usuarios; el alumno nace con la solicitud de matrícula. */
    private static final Set<String> ROLES_CREABLES = Set.of("ADMINISTRADOR", "DOCENTE", "APODERADO");

    public record NuevoUsuario(String rol, String nombres, String apellidos, String documento, String correo,
                               String cargo, String especialidad, String telefono) {
    }

    public record CambiosUsuario(String nombres, String apellidos, String documento, String correo, String cargo,
                                 String especialidad, String telefono) {
    }

    public record DetalleUsuario(String id, String nombres, String apellidos, String documento, String correo,
                                 String rol, EstadoCuenta estado, String cargo, String codigoDocente,
                                 String especialidad, String telefono, String codigoAlumno,
                                 OffsetDateTime invitacionVenceEn, long version) {
    }

    private final UsuarioRepository usuarios;
    private final RolAccesoRepository roles;
    private final AdministradorRepository administradores;
    private final DocenteRepository docentes;
    private final ApoderadoRepository apoderados;
    private final AlumnoRepository alumnos;
    private final TokenRecuperacionRepository tokens;
    private final TokensRefresco tokensRefresco;
    private final CacheCuentas cacheCuentas;
    private final Invitaciones invitaciones;
    private final VerificadorVinculoAcademico vinculo;
    private final ContextoUsuario contexto;
    private final Clock reloj;

    GestionUsuariosService(UsuarioRepository usuarios, RolAccesoRepository roles,
                           AdministradorRepository administradores, DocenteRepository docentes,
                           ApoderadoRepository apoderados, AlumnoRepository alumnos,
                           TokenRecuperacionRepository tokens, TokensRefresco tokensRefresco,
                           CacheCuentas cacheCuentas, Invitaciones invitaciones,
                           VerificadorVinculoAcademico vinculo, ContextoUsuario contexto, Clock reloj) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.administradores = administradores;
        this.docentes = docentes;
        this.apoderados = apoderados;
        this.alumnos = alumnos;
        this.tokens = tokens;
        this.tokensRefresco = tokensRefresco;
        this.cacheCuentas = cacheCuentas;
        this.invitaciones = invitaciones;
        this.vinculo = vinculo;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public DetalleUsuario obtener(String id) {
        return detalleDe(buscar(id));
    }

    /** Crea la cuenta pendiente y envía la invitación (el correo sale tras el commit). */
    @Transactional
    @Auditable(accion = "CREAR_USUARIO", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#result.id",
            descripcion = "Alta de cuenta de usuario con invitación para definir la contraseña")
    public DetalleUsuario crear(NuevoUsuario n) {
        if ("ALUMNO".equals(n.rol())) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Los estudiantes no se crean aquí: nacen con la solicitud de matrícula y solo se habilitan");
        }
        if (!ROLES_CREABLES.contains(n.rol())) {
            throw invalido("rol", "El rol debe ser ADMINISTRADOR, DOCENTE o APODERADO");
        }
        String correo = n.correo().trim();
        String documento = n.documento().trim();
        validarDocumentoNuevo(documento);
        if ("ADMINISTRADOR".equals(n.rol()) && vacio(n.cargo())) {
            throw invalido("cargo", "El cargo es obligatorio para el personal administrativo");
        }
        validarUnicidad(null, documento, correo);
        RolAcceso rol = roles.findByCodigo(n.rol()).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.ERROR_INTERNO, "Falta el rol " + n.rol() + " en el catálogo"));

        Usuario usuario = Usuario.pendienteDeInvitacion(n.nombres().trim(), n.apellidos().trim(), documento, correo, rol);
        guardar(usuario);
        switch (n.rol()) {
            case "ADMINISTRADOR" -> administradores.save(new Administrador(usuario.getId(), n.cargo().trim()));
            case "DOCENTE" -> {
                docentes.tomarCandadoDeCodigos();
                docentes.save(new Docente(usuario.getId(), "DOC-%03d".formatted(docentes.siguienteNumeroDeCodigo()),
                        vacioANulo(n.especialidad())));
            }
            default -> apoderados.save(new Apoderado(usuario.getId(), vacioANulo(n.telefono())));
        }
        ContextoAuditoria.registrarCambio("rol", null, n.rol());
        ContextoAuditoria.registrarCambio("documento", null, documento);
        ContextoAuditoria.registrarCambio("correo", null, correo);
        ContextoAuditoria.registrarCambio("estado", null, EstadoCuenta.PENDIENTE);
        invitaciones.enviar(usuario);
        return detalleDe(usuario);
    }

    /** El rol no cambia. Si la cuenta sigue pendiente y cambió el correo, la invitación se reenvía al correo nuevo. */
    @Transactional
    @Auditable(accion = "MODIFICAR_USUARIO", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#id",
            descripcion = "Modificación de los datos de una cuenta de usuario")
    public DetalleUsuario modificar(String id, CambiosUsuario c, String ifMatch) {
        Usuario usuario = buscar(id);
        String rol = usuario.getRolAcceso().getCodigo();
        if ("ALUMNO".equals(rol)) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Los datos del estudiante se editan en Estudiantes (RF-02)");
        }
        Etags.verificar(usuario.getVersion(), ifMatch);
        String correo = c.correo().trim();
        String documento = c.documento().trim();
        if (!documento.equals(usuario.getDocumentoIdentidad())) {
            validarDocumentoNuevo(documento);
        }
        if ("ADMINISTRADOR".equals(rol) && vacio(c.cargo())) {
            throw invalido("cargo", "El cargo es obligatorio para el personal administrativo");
        }
        validarUnicidad(usuario, documento, correo);

        registrar("nombres", usuario.getNombres(), c.nombres().trim());
        registrar("apellidos", usuario.getApellidos(), c.apellidos().trim());
        registrar("documento", usuario.getDocumentoIdentidad(), documento);
        boolean correoCambio = !correo.equalsIgnoreCase(Objects.toString(usuario.getCorreo(), ""));
        registrar("correo", usuario.getCorreo(), correo);
        usuario.actualizarDatosPersonales(c.nombres().trim(), c.apellidos().trim(), documento, correo, reloj.instant());
        switch (rol) {
            case "ADMINISTRADOR" -> administradores.findByUsuarioId(id).ifPresent(a -> {
                registrar("cargo", a.getCargo(), c.cargo().trim());
                a.cambiarCargo(c.cargo().trim());
            });
            case "DOCENTE" -> docentes.findByUsuarioId(id).ifPresent(d -> {
                registrar("especialidad", d.getEspecialidad(), vacioANulo(c.especialidad()));
                d.cambiarEspecialidad(vacioANulo(c.especialidad()));
            });
            default -> apoderados.findByUsuarioId(id).ifPresent(a -> {
                registrar("telefono", a.getNumeroTelefono(), vacioANulo(c.telefono()));
                a.actualizarTelefono(vacioANulo(c.telefono()));
            });
        }
        guardar(usuario);
        if (correoCambio && esPendiente(usuario)) {
            invitaciones.enviar(usuario);
        }
        return detalleDe(usuario);
    }

    /**
     * Habilita la cuenta. Un alumno exige matrícula activa (FA-2, puerto {@link VerificadorVinculoAcademico}); el
     * apoderado y el personal no exigen vínculo (S-82). Si la cuenta ya tiene contraseña queda activa; si no, se le
     * envía la invitación y queda pendiente hasta que la defina. {@code correo} solo se usa para completar una cuenta
     * que no lo tiene (el alumno nace sin correo).
     */
    @Transactional
    @Auditable(accion = "ACTIVAR_USUARIO", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#id",
            descripcion = "Habilitación de una cuenta de usuario")
    public DetalleUsuario activar(String id, String correoNuevo) {
        Usuario usuario = buscar(id);
        if (usuario.isActivo()) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "La cuenta ya está activa");
        }
        exigirVinculo(usuario);
        EstadoCuenta anterior = estadoDe(usuario, reloj.instant());
        if (usuario.sinContrasena()) {
            String correo = vacio(correoNuevo) ? usuario.getCorreo() : correoNuevo.trim();
            if (vacio(correo)) {
                throw invalido("correo", "Indica el correo al que se enviará la invitación");
            }
            validarUnicidad(usuario, usuario.getDocumentoIdentidad(), correo);
            registrar("correo", usuario.getCorreo(), correo);
            usuario.asignarCorreo(correo);
            guardar(usuario);
            invitaciones.enviar(usuario);
        } else {
            usuario.activar();
            guardar(usuario);
        }
        ContextoAuditoria.registrarCambio("estado", anterior, estadoDe(usuario, reloj.instant()));
        return detalleDe(usuario);
    }

    /**
     * Desactiva la cuenta y corta sus sesiones de inmediato: se revocan los refresh tokens, se anulan los enlaces
     * pendientes y se descarta la caché, así que el siguiente request con su token de acceso da 401.
     */
    @Transactional
    @Auditable(accion = "DESACTIVAR_USUARIO", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#id",
            descripcion = "Desactivación de una cuenta de usuario")
    public DetalleUsuario desactivar(String id) {
        Usuario usuario = buscar(id);
        if (contexto.idUsuarioActual().filter(id::equals).isPresent()) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "No puedes desactivar tu propia cuenta");
        }
        if (!usuario.isActivo()) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA, "La cuenta ya está inactiva");
        }
        Instant ahora = reloj.instant();
        EstadoCuenta anterior = estadoDe(usuario, ahora);
        usuario.desactivar();
        guardar(usuario);
        tokensRefresco.revocarTodos(id);
        tokens.anularPendientes(id, ahora);
        ContextoAuditoria.registrarCambio("estado", anterior, EstadoCuenta.INACTIVO);
        return detalleDe(usuario);
    }

    /** «Reenviar invitación»: solo para una cuenta pendiente; la anterior queda anulada. */
    @Transactional
    @Auditable(accion = "REENVIAR_INVITACION", modulo = "SEGURIDAD", tipoEntidad = "Usuario", idEntidad = "#id",
            descripcion = "Reenvío de la invitación para definir la contraseña")
    public DetalleUsuario reenviarInvitacion(String id) {
        Usuario usuario = buscar(id);
        if (!esPendiente(usuario)) {
            throw new ExcepcionNegocio(CodigoError.SOLICITUD_INVALIDA,
                    "Solo se reenvía la invitación a una cuenta que aún no definió su contraseña");
        }
        exigirVinculo(usuario);
        invitaciones.enviar(usuario);
        ContextoAuditoria.registrarCambio("invitacion", null, "reenviada a " + usuario.getCorreo());
        return detalleDe(usuario);
    }

    // ---- Apoyo -------------------------------------------------------------------------------------------

    private void exigirVinculo(Usuario usuario) {
        if (!"ALUMNO".equals(usuario.getRolAcceso().getCodigo())) {
            return;
        }
        boolean conMatricula = alumnos.findByUsuarioId(usuario.getId())
                .map(a -> vinculo.alumnoConMatriculaActiva(a.getId())).orElse(false);
        if (!conMatricula) {
            throw new ExcepcionNegocio(CodigoError.VINCULO_ACADEMICO_INEXISTENTE,
                    "El estudiante no tiene una matrícula activa; no se puede habilitar su cuenta");
        }
    }

    private Usuario buscar(String id) {
        return usuarios.findConPermisosById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el usuario"));
    }

    private static boolean esPendiente(Usuario u) {
        return !u.isActivo() && u.sinContrasena() && u.getCorreo() != null;
    }

    static EstadoCuenta estadoDe(Usuario u, Instant ahora) {
        if (u.estaBloqueada(ahora)) {
            return EstadoCuenta.BLOQUEADO;
        }
        if (u.isActivo()) {
            return EstadoCuenta.ACTIVO;
        }
        return esPendiente(u) ? EstadoCuenta.PENDIENTE : EstadoCuenta.INACTIVO;
    }

    private DetalleUsuario detalleDe(Usuario u) {
        String id = u.getId();
        String rol = u.getRolAcceso().getCodigo();
        String cargo = null;
        String codigoDocente = null;
        String especialidad = null;
        String telefono = null;
        String codigoAlumno = null;
        switch (rol) {
            case "ADMINISTRADOR" -> cargo = administradores.findByUsuarioId(id).map(Administrador::getCargo).orElse(null);
            case "DOCENTE" -> {
                Docente d = docentes.findByUsuarioId(id).orElse(null);
                codigoDocente = d == null ? null : d.getCodigoDocente();
                especialidad = d == null ? null : d.getEspecialidad();
            }
            case "APODERADO" -> telefono = apoderados.findByUsuarioId(id).map(Apoderado::getNumeroTelefono).orElse(null);
            default -> codigoAlumno = alumnos.findByUsuarioId(id).map(Alumno::getCodigoAlumno).orElse(null);
        }
        OffsetDateTime vence = esPendiente(u)
                ? tokens.vencimientoPendiente(id).map(i -> i.atZone(reloj.getZone()).toOffsetDateTime()).orElse(null)
                : null;
        return new DetalleUsuario(id, u.getNombres(), u.getApellidos(), u.getDocumentoIdentidad(), u.getCorreo(), rol,
                estadoDe(u, reloj.instant()), cargo, codigoDocente, especialidad, telefono, codigoAlumno, vence,
                u.getVersion());
    }

    /** Documento y correo únicos (FA-1, 409). {@code actual} es la cuenta que se edita, o nulo en un alta. */
    private void validarUnicidad(Usuario actual, String documento, String correo) {
        String propio = actual == null ? "" : actual.getId();
        if (usuarios.existsByDocumentoIdentidadAndIdNot(documento, propio)) {
            throw new ExcepcionNegocio(CodigoError.DOCUMENTO_DUPLICADO,
                    "Ya existe una cuenta registrada con el documento " + documento);
        }
        if (usuarios.existeCorreoDeOtro(correo, propio)) {
            throw new ExcepcionNegocio(CodigoError.CORREO_DUPLICADO,
                    "Ya existe una cuenta registrada con el correo " + correo);
        }
    }

    /** Un documento nuevo debe ser un DNI de 8 dígitos (S-50). */
    private static void validarDocumentoNuevo(String documento) {
        if (!documento.matches("\\d{8}")) {
            throw invalido("documento", "El documento de identidad debe tener 8 dígitos");
        }
    }

    private static ExcepcionNegocio invalido(String campo, String mensaje) {
        return new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, mensaje)
                .con("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
    }

    /** Guarda y fuerza el UPDATE/INSERT ahora; una carrera por documento o correo cae en el índice único. */
    private void guardar(Usuario usuario) {
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            throw causa.contains("ux_usuario_correo") ? new ExcepcionNegocio(CodigoError.CORREO_DUPLICADO)
                    : new ExcepcionNegocio(CodigoError.DOCUMENTO_DUPLICADO);
        }
        cacheCuentas.invalidar(usuario.getId());
    }

    private static void registrar(String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            ContextoAuditoria.registrarCambio(campo, anterior, nuevo);
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String vacioANulo(String texto) {
        return vacio(texto) ? null : texto.trim();
    }
}
