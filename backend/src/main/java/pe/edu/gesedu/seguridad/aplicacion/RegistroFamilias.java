package pe.edu.gesedu.seguridad.aplicacion;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;
import pe.edu.gesedu.seguridad.aplicacion.DirectorioFamilias.AlumnoResumen;
import pe.edu.gesedu.seguridad.dominio.Alumno;
import pe.edu.gesedu.seguridad.dominio.Usuario;
import pe.edu.gesedu.seguridad.infraestructura.AlumnoRepository;
import pe.edu.gesedu.seguridad.infraestructura.ApoderadoRepository;
import pe.edu.gesedu.seguridad.infraestructura.RolAccesoRepository;
import pe.edu.gesedu.seguridad.infraestructura.UsuarioRepository;

/**
 * Altas de alumnos y datos de contacto que la solicitud de matrícula necesita (UC-01, RF-02). El alumno que
 * solicita un apoderado queda como cuenta sin acceso; se habilita después (UC-24).
 */
@Service
public class RegistroFamilias {

    /** Datos del estudiante tal como los ingresa el apoderado. */
    public record DatosAlumno(String nombres, String apellidos, String documento, LocalDate fechaNacimiento,
                              String parentesco) {
    }

    /** El alumno y si se creó en esta operación (nuevo ingreso) o ya existía (recurrente). */
    public record AlumnoRegistrado(AlumnoResumen alumno, boolean creado) {
    }

    private final DirectorioFamilias directorio;
    private final UsuarioRepository usuarios;
    private final AlumnoRepository alumnos;
    private final ApoderadoRepository apoderados;
    private final RolAccesoRepository roles;

    RegistroFamilias(DirectorioFamilias directorio, UsuarioRepository usuarios, AlumnoRepository alumnos,
                     ApoderadoRepository apoderados, RolAccesoRepository roles) {
        this.directorio = directorio;
        this.usuarios = usuarios;
        this.alumnos = alumnos;
        this.apoderados = apoderados;
        this.roles = roles;
    }

    /**
     * Devuelve el alumno con ese documento si ya es hijo del apoderado; si no existe, lo crea con el código
     * {@code AAAA-NNNN} del año de la solicitud.
     *
     * @throws ExcepcionNegocio {@code DOCUMENTO_DUPLICADO} si el documento pertenece a una cuenta que no es de un
     *                          hijo de este apoderado
     */
    @Transactional
    public AlumnoRegistrado obtenerORegistrar(String apoderadoId, DatosAlumno datos, int anio) {
        // Las altas se serializan: dos solicitudes simultáneas del mismo documento no crean dos cuentas
        alumnos.tomarCandadoDeCodigos();
        Optional<AlumnoResumen> existente = directorio.alumnoPorDocumento(datos.documento());
        if (existente.isPresent()) {
            if (!existente.get().apoderadoId().equals(apoderadoId)) {
                throw new ExcepcionNegocio(CodigoError.DOCUMENTO_DUPLICADO,
                        "Ya existe un estudiante registrado con el documento " + datos.documento()
                                + " a cargo de otro apoderado");
            }
            return new AlumnoRegistrado(existente.get(), false);
        }
        if (usuarios.existsByDocumentoIdentidad(datos.documento())) {
            throw new ExcepcionNegocio(CodigoError.DOCUMENTO_DUPLICADO,
                    "Ya existe una cuenta registrada con el documento " + datos.documento());
        }
        var rol = roles.findByCodigo("ALUMNO").orElseThrow(() ->
                new ExcepcionNegocio(CodigoError.ERROR_INTERNO, "Falta el rol ALUMNO en el catálogo"));
        Usuario cuenta = usuarios.save(Usuario.sinAcceso(datos.nombres().trim(), datos.apellidos().trim(),
                datos.documento(), rol));
        String codigo = "%d-%04d".formatted(anio, alumnos.siguienteNumeroDeCodigo(anio));
        Alumno alumno = alumnos.saveAndFlush(new Alumno(cuenta.getId(), codigo, datos.fechaNacimiento(), apoderadoId,
                datos.parentesco()));
        return new AlumnoRegistrado(directorio.alumnos(java.util.List.of(alumno.getId())).get(alumno.getId()), true);
    }

    /** El apoderado actualiza su teléfono de contacto (RF-02). Texto vacío lo borra. */
    @Transactional
    public void actualizarTelefono(String apoderadoId, String telefono) {
        apoderados.findById(apoderadoId).ifPresent(a -> a.actualizarTelefono(
                telefono == null || telefono.isBlank() ? null : telefono.trim()));
    }
}
