package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.compartido.web.Etags;
import pe.edu.gesedu.seguridad.dominio.Alumno;
import pe.edu.gesedu.seguridad.dominio.Apoderado;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.AlumnoRepository;
import pe.edu.gesedu.seguridad.infraestructura.ApoderadoRepository;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/**
 * Edición de los datos personales de estudiantes y apoderados por el administrador (RF-02, C-43). La versión de la
 * cuenta ({@code usuario.version}) hace de {@code ETag}: cada edición la sube (se fuerza el UPDATE aunque solo cambie
 * un dato del perfil) y una edición que partió de una versión anterior responde 409. El documento y el correo siguen siendo únicos.
 */
@Service
public class DatosPersonalesService {

    public record FichaAlumno(String id, String codigo, String nombres, String apellidos, String documento,
                              String correo, LocalDate fechaNacimiento, String parentesco, String apoderadoId,
                              String apoderado, boolean cuentaActiva, long version) {
    }

    public record FichaApoderado(String id, String nombres, String apellidos, String documento, String correo,
                                 String telefono, boolean cuentaActiva, long version) {
    }

    public record CambiosAlumno(String nombres, String apellidos, String documento, String correo,
                                LocalDate fechaNacimiento, String parentesco) {
    }

    public record CambiosApoderado(String nombres, String apellidos, String documento, String correo, String telefono) {
    }

    private final UsuarioRepository usuarios;
    private final AlumnoRepository alumnos;
    private final ApoderadoRepository apoderados;
    private final CacheCuentas cacheCuentas;
    private final Clock reloj;

    DatosPersonalesService(UsuarioRepository usuarios, AlumnoRepository alumnos, ApoderadoRepository apoderados,
                           CacheCuentas cacheCuentas, Clock reloj) {
        this.reloj = reloj;
        this.usuarios = usuarios;
        this.alumnos = alumnos;
        this.apoderados = apoderados;
        this.cacheCuentas = cacheCuentas;
    }

    // ---- Estudiante --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FichaAlumno obtenerAlumno(String id) {
        Alumno alumno = buscarAlumno(id);
        return fichaDe(alumno, usuarios.findById(alumno.getUsuarioId()).orElseThrow());
    }

    @Transactional
    @Auditable(accion = "EDITAR_DATOS_ESTUDIANTE", modulo = "SEGURIDAD", tipoEntidad = "Alumno", idEntidad = "#id",
            descripcion = "Edición de los datos personales de un estudiante")
    public FichaAlumno actualizarAlumno(String id, CambiosAlumno c, String ifMatch) {
        Alumno alumno = buscarAlumno(id);
        Usuario usuario = cargarParaEditar(alumno.getUsuarioId(), ifMatch);
        String correo = normalizar(c.correo());
        validarCuenta(usuario, c.documento(), correo);

        registrar("nombres", usuario.getNombres(), c.nombres().trim());
        registrar("apellidos", usuario.getApellidos(), c.apellidos().trim());
        registrar("documento", usuario.getDocumentoIdentidad(), c.documento());
        registrar("correo", usuario.getCorreo(), correo);
        registrar("fechaNacimiento", alumno.getFechaNacimiento(), c.fechaNacimiento());
        registrar("parentesco", alumno.getParentescoApoderado(), c.parentesco().trim());
        usuario.actualizarDatosPersonales(c.nombres().trim(), c.apellidos().trim(), c.documento(), correo, reloj.instant());
        alumno.actualizarDatos(c.fechaNacimiento(), c.parentesco().trim());
        guardar(usuario);
        return fichaDe(alumno, usuario);
    }

    // ---- Apoderado ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FichaApoderado obtenerApoderado(String id) {
        Apoderado apoderado = buscarApoderado(id);
        return fichaDe(apoderado, usuarios.findById(apoderado.getUsuarioId()).orElseThrow());
    }

    @Transactional
    @Auditable(accion = "EDITAR_DATOS_APODERADO", modulo = "SEGURIDAD", tipoEntidad = "Apoderado", idEntidad = "#id",
            descripcion = "Edición de los datos personales de un apoderado")
    public FichaApoderado actualizarApoderado(String id, CambiosApoderado c, String ifMatch) {
        Apoderado apoderado = buscarApoderado(id);
        Usuario usuario = cargarParaEditar(apoderado.getUsuarioId(), ifMatch);
        String correo = normalizar(c.correo());
        String telefono = normalizar(c.telefono());
        validarCuenta(usuario, c.documento(), correo);

        registrar("nombres", usuario.getNombres(), c.nombres().trim());
        registrar("apellidos", usuario.getApellidos(), c.apellidos().trim());
        registrar("documento", usuario.getDocumentoIdentidad(), c.documento());
        registrar("correo", usuario.getCorreo(), correo);
        registrar("telefono", apoderado.getNumeroTelefono(), telefono);
        usuario.actualizarDatosPersonales(c.nombres().trim(), c.apellidos().trim(), c.documento(), correo, reloj.instant());
        apoderado.actualizarTelefono(telefono);
        guardar(usuario);
        return fichaDe(apoderado, usuario);
    }

    // ---- Apoyo -------------------------------------------------------------------------------------------

    private Alumno buscarAlumno(String id) {
        return alumnos.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el estudiante"));
    }

    private Apoderado buscarApoderado(String id) {
        return apoderados.findById(id).orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.RECURSO_NO_ENCONTRADO, "No existe el apoderado"));
    }

    private Usuario cargarParaEditar(String usuarioId, String ifMatch) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        Etags.verificar(usuario.getVersion(), ifMatch);
        return usuario;
    }

    /** Documento y correo únicos (409); una cuenta activa no puede quedarse sin correo (RF-52). */
    private void validarCuenta(Usuario usuario, String documento, String correo) {
        if (usuarios.existsByDocumentoIdentidadAndIdNot(documento, usuario.getId())) {
            throw new ExcepcionNegocio(CodigoError.DOCUMENTO_DUPLICADO,
                    "Ya existe una cuenta registrada con el documento " + documento);
        }
        // Un documento nuevo debe ser un DNI de 8 dígitos; el que ya tiene la cuenta se conserva tal cual (S-50)
        if (!documento.equals(usuario.getDocumentoIdentidad()) && !documento.matches("\\d{8}")) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "El documento de identidad debe tener 8 dígitos")
                    .con("errores", List.of(Map.of("campo", "documento",
                            "mensaje", "El documento de identidad debe tener 8 dígitos")));
        }
        if (correo != null && usuarios.existeCorreoDeOtro(correo, usuario.getId())) {
            throw new ExcepcionNegocio(CodigoError.CORREO_DUPLICADO, "Ya existe una cuenta registrada con el correo " + correo);
        }
        if (correo == null && usuario.isActivo()) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA, "Una cuenta activa necesita un correo para iniciar sesión");
        }
    }

    /** Guarda y fuerza el UPDATE ahora: así la versión nueva ya está en la entidad y una carrera se detecta aquí. */
    private void guardar(Usuario usuario) {
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            throw causa.contains("ux_usuario_correo") ? new ExcepcionNegocio(CodigoError.CORREO_DUPLICADO)
                    : new ExcepcionNegocio(CodigoError.DOCUMENTO_DUPLICADO);
        }
        // Si el correo cambió, la caché de la cuenta se descarta junto con sus permisos
        cacheCuentas.invalidar(usuario.getId());
    }

    private static void registrar(String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            ContextoAuditoria.registrarCambio(campo, anterior, nuevo);
        }
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private FichaAlumno fichaDe(Alumno a, Usuario u) {
        Apoderado ap = apoderados.findById(a.getApoderadoId()).orElse(null);
        String nombreApoderado = ap == null ? null : usuarios.findById(ap.getUsuarioId())
                .map(x -> x.getNombres() + " " + x.getApellidos()).orElse(null);
        return new FichaAlumno(a.getId(), a.getCodigoAlumno(), u.getNombres(), u.getApellidos(), u.getDocumentoIdentidad(),
                u.getCorreo(), a.getFechaNacimiento(), a.getParentescoApoderado(), a.getApoderadoId(), nombreApoderado,
                u.isActivo(), u.getVersion());
    }

    private FichaApoderado fichaDe(Apoderado a, Usuario u) {
        return new FichaApoderado(a.getId(), u.getNombres(), u.getApellidos(), u.getDocumentoIdentidad(), u.getCorreo(),
                a.getNumeroTelefono(), u.isActivo(), u.getVersion());
    }
}
