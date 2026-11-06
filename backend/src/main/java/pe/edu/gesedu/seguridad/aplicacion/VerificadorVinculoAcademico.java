package pe.edu.gesedu.seguridad.aplicacion;

/**
 * Puerto (SPI) con el que {@code seguridad} comprueba el vínculo académico de una cuenta antes de habilitarla
 * (UC-24 FA-2) sin depender de {@code matricula}. Lo implementa el módulo {@code matricula}.
 */
public interface VerificadorVinculoAcademico {

    /**
     * @return {@code true} si el alumno tiene una matrícula confirmada en un año escolar que no está cerrado
     */
    boolean alumnoConMatriculaActiva(String alumnoId);
}
